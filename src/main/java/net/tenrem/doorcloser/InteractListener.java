package net.tenrem.doorcloser;

import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;

import org.bukkit.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.*;

import java.util.HashMap;
import java.util.Map;

public final class InteractListener implements Listener 
{
	// this is a bukkit / minecraft constant. Put here only for clarity
	private static final int TICKS_PER_SECOND = 20;

	private final DoorCloserPlugin _plugin;
	
	public InteractListener(DoorCloserPlugin plugin) {
		_plugin = plugin;
	}

	
	// This is going to fire for every interaction, so need to exit it quickly if it's not what we want to handle
	@EventHandler(priority=EventPriority.LOWEST)
	public void blockInteract(PlayerInteractEvent e) 
	{
		// if the event has been canceled and we're not ignoring canceled events, quit
		// the only reason we check here instead of in the @EventHandler directive is
		// so this can be changed in the config file
		if (e.isCancelled() && Settings.ignoreCanceledEvents)
		{
			return;
		}

		Action action = e.getAction();
		
		// right clicks only
		if (action == Action.RIGHT_CLICK_BLOCK) {
			Block clickedBlock = e.getClickedBlock();
			 
			// check to see if we care about this type of block. In our case, we want
			// something like a gate, trap door or door.
			if (isOpenable(clickedBlock.getType())) {
				 // check to see if we're ignoring creative mode
				if ((e.getPlayer().getGameMode() == GameMode.CREATIVE) && (Settings.ignoreIfInCreative))
				{
					return;
				}
	
				 // check to see if we're ignoring sneaking
				if ((e.getPlayer().isSneaking()) && (Settings.ignoreIfSneaking)) return;
				
				Material blockDoorType = clickedBlock.getType();
				 
				// check to see if it is a type of block we want to close. Note that
				// we're not doing any type checking on these. I don't want to have to
				// maintain a finite list of doors/gates that has to be updated with
				// each version of Minecraft.
				 
				// todo: should check to see if we were clicking it closed. If so, don't schedule a close
				
				if (isTrapdoor(blockDoorType) && Settings.trapDoorsInScope.contains(blockDoorType))
				{
					ScheduleClose(clickedBlock, null, Settings.secondsToRemainOpen);				 
				}
				 
				else if (isFenceGate(blockDoorType) && Settings.gatesInScope.contains(blockDoorType))
				{
					ScheduleClose(clickedBlock, null, Settings.secondsToRemainOpen);	
				}
				 
				else if (isDoor(blockDoorType) && Settings.doorsInScope.contains(blockDoorType))
				{
				//	_plugin.getLogger().info("DEBUG: Normal door found: " + clickedBlock.getType().toString());

					Door door1 = (Door) clickedBlock.getState().getData();

					//_plugin.getLogger().info("DEBUG: Clicked door state during event: isOpen()=" + door1.isOpen());

					// check to see if they clicked the top of the door. If so, change to the block below it.
					// Necessary because server only supports the door operations on the lower block.
					// Do this only for doors so as not to mess up stacked gates or stacked trap doors
					// This could fail if you somehow manage to get two doors stacked on top of each other.
					if (door1.isTopHalf())
					{
						//_plugin.getLogger().info("DEBUG: Handling click on top half of door");

						clickedBlock = clickedBlock.getRelative(BlockFace.DOWN);
						door1 = (Door) clickedBlock.getState().getData();
					}
				
					// here's where we check the double-door stuff.
					Block pairedDoorBlock = GetPairedDoorBlockIfDoubleDoor(clickedBlock);				
					Door pairedDoor = DoorFromBlock(pairedDoorBlock);

					if (pairedDoorBlock == null || pairedDoor == null)
					{
						// isOpen is not yet true at this point
						// would be better to find a way to update the state and THEN do the check
						if (!door1.isOpen())
						{
							// standard single door. Just close it.
							ScheduleClose(clickedBlock, null, Settings.secondsToRemainOpen);				 			 
						}
					}
					else 
					{
						// sync double door OPEN if configured to do so. This is an add-on to
						// what the rest of this plugin is handling.
						// Note that the clicked door's state doesn't change to opened until after the event
						// or it could just be a timing thing.
						if (Settings.synchronizeDoubleDoorOpen)
						{							
							OpenDoor(pairedDoorBlock);

							// door was just opened. sync closing both doors
							ScheduleClose(clickedBlock, pairedDoorBlock, Settings.secondsToRemainOpen);
						}

						// Sync double door manual close. This is an add-on to what the plugin handles
						if (pairedDoorBlock != null && pairedDoor != null)
						{
							// sync double door close if configured to do so
							if ((door1.isOpen() || pairedDoor.isOpen()) && Settings.synchronizeDoubleDoorClose)
							{
								CloseDoor(pairedDoorBlock);
								PlayCloseNoise(pairedDoorBlock);
							}
						}
					}
					
				}
				else
				{
					// be sure to comment this out or change log level or else it will spam the logs
				//	_plugin.getLogger().info("DEBUG: Unexpected block: " + blockDoorType.toString());
				}
			}
		}
	}
	 
	
	// handles getting the Openable from a specific block
	// returns null if not a Openable
	private Openable OpenableFromBlock(Block block)
	{
		if (block != null)
		{
			MaterialData data = block.getState().getData();

			if (data != null && isOpenable(block.getType()))
			{
				return (Openable) data;
			}
			else
			{
				return null;
			}
		}
		else
		{
			return null;
		}
	}

	// handles getting the door from a specific block
	// returns null if not a door
	private Door DoorFromBlock(Block block)
	{
		if (block != null) {
			MaterialData data = block.getState().getData();

			if (data != null && isDoor(block.getType())) {
				return (Door) block.getState().getData();
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	private void PlayCloseNoise(Block doorBlock)
	{
		if (doorBlock != null) {
			if (Settings.playSound) {
				if (isTrapdoor(doorBlock.getType())) {
					doorBlock.getWorld().playSound(doorBlock.getLocation(), SoundUtils.DOOR_CLOSE.getSound(), 1, 1);
				} else if (isFenceGate(doorBlock.getType())) {
					doorBlock.getWorld().playSound(doorBlock.getLocation(), SoundUtils.GATE_CLOSE.getSound(), 1, 1);
				} else if (isDoor(doorBlock.getType())) {
					doorBlock.getWorld().playSound(doorBlock.getLocation(), SoundUtils.DOOR_CLOSE.getSound(), 1, 1);
				}
			}

		}
	}

	public void ScheduleClose(Block door1Block, Block pairedDoorBlock, int seconds)
	{		
		// Schedule the closing to happen at apx "seconds" seconds from now.
		Bukkit.getScheduler().runTaskLater(_plugin, new Runnable()
			{
				@Override
				public void run()
				{
					//_plugin.getLogger().info("DEBUG: In scheduled door close .");

					if (door1Block != null)
					{
						Openable door1Data = OpenableFromBlock(door1Block);

						if (door1Data != null)
						{
							if (door1Data.isOpen())
							{
								CloseDoor(door1Block);
								PlayCloseNoise(door1Block);
							}
						}
						else
						{
							_plugin.getLogger().warning("Tried to close door block, but block data was null or not correct type.");
						}
					}
					else
					{
						_plugin.getLogger().warning("Null main door block sent to ScheduleClose.");
					}


					if (pairedDoorBlock != null)
					{
						Openable pairedDoorData = OpenableFromBlock(pairedDoorBlock);

						if (pairedDoorData != null)
						{						
							if (pairedDoorData.isOpen())
							{
								CloseDoor(pairedDoorBlock);
								PlayCloseNoise(pairedDoorBlock);
							}
						}
						else
						{
							_plugin.getLogger().warning("Tried to close paired door block, but block data was null or not correct type.");	
						}
					}
					else
					{
						// this would typically be null for single doors, trap doors, etc.
						// do nothing
					}
				}
		
			}, (long)seconds * TICKS_PER_SECOND);

	} 


	private Block GetPairedDoorBlockIfDoubleDoor(Block doorBlock)
	{

		if (isDoor(doorBlock.getType()))
		{
			//Door doorData = (Door)(doorBlock.getBlockData());
			Door doorData = (Door) doorBlock.getState().getData();
			boolean hinge = doorData.getHinge();

			Block pairedDoor;

			BlockFace face = doorData.getFacing();

			//_plugin.getLogger().info("DEBUG: door face=" + face.toString());
			//_plugin.getLogger().info("DEBUG: door isOpen()=" + doorData.isOpen());
			//_plugin.getLogger().info("DEBUG: door hinge=" + hinge.toString());

			switch (face) {
				case NORTH:
					if (!hinge) {
						pairedDoor = doorBlock.getRelative(BlockFace.EAST);	
					} else {
						pairedDoor = doorBlock.getRelative(BlockFace.WEST);	
					}
					break;
				case SOUTH:
					if (!hinge) {
						pairedDoor = doorBlock.getRelative(BlockFace.WEST);	
					} else {
						pairedDoor = doorBlock.getRelative(BlockFace.EAST);	
					}
					break;
				case EAST:
					if (!hinge) {
						pairedDoor = doorBlock.getRelative(BlockFace.SOUTH);	
					} else {
						pairedDoor = doorBlock.getRelative(BlockFace.NORTH);	
					}
					break;
				case WEST:
					if (!hinge) {
						pairedDoor = doorBlock.getRelative(BlockFace.NORTH);	
					} else {
						pairedDoor = doorBlock.getRelative(BlockFace.SOUTH);	
					}
					break;
				default:
					pairedDoor = null;
					break;
			}

			if (pairedDoor != null)
			{
				// check the block we found that is opposite the hinge. If it
				// is a door and has a hinge that is opposite this one, then
				// it is our pair

				// check to see if that block is actually a door
				if (isDoor(pairedDoor.getType()))
				{
					Door doorData2 = (Door) pairedDoor.getState().getData();

					//_plugin.getLogger().info("DEBUG: Door neighbor is a door.");
					//_plugin.getLogger().info("DEBUG: paired door face=" + door2.getFacing().toString());
					//_plugin.getLogger().info("DEBUG: paired door isOpen()=" + door2.isOpen());
					//_plugin.getLogger().info("DEBUG: paired door hinge=" + door2.getHinge().toString());


					if ((!hinge && doorData2.getHinge()) || (hinge && !doorData2.getHinge())) {
						//_plugin.getLogger().info("DEBUG: Found paired / double door");

						// we're good!
						return pairedDoor;
					} else {
						//_plugin.getLogger().info("DEBUG: Neighbor has hinge on same side. Not a double door");

						// neighbor block has hinge on same side
						// not the pair for this door
						return null;
					}
				}
				else
				{
					//_plugin.getLogger().info("DEBUG: Neighbor block is not a door. Not a double door.");

					// neighbor block is not a door.
					// door is a single door, not a double door
					return null;
				}
			}
			else
			{
				// no block next door. That would be ... odd
				// door is a single door, not double
				return null;
			}
		}
		else
		{
			_plugin.getLogger().warning("Bogus block type passed into GetPairedDoorBlockIfDoubleDoor.");
			return null;
		}
	}


	private void OpenDoor(Block doorBlock) {
		MaterialData data = doorBlock.getState().getData();

		if (isOpenable(doorBlock.getType())) {
			if(data instanceof Door) {
				toggleDoor(doorBlock, true);
			} else if(data instanceof Gate) {
				toggleGate(doorBlock, true);
			} else if(data instanceof TrapDoor) {
				toggleTrapdoor(doorBlock, true);
			}
		}
	}

	private void CloseDoor(Block doorBlock) {
		MaterialData data = doorBlock.getState().getData();

		if (isOpenable(doorBlock.getType())) {
			if(data instanceof Door) {
				toggleDoor(doorBlock, false);
			} else if(data instanceof Gate) {
				toggleGate(doorBlock, false);
			} else if(data instanceof TrapDoor) {
				toggleTrapdoor(doorBlock, false);
			}
		}
	}

	private boolean isOpenable(Material block) {
		return isTrapdoor(block) || isFenceGate(block) || isDoor(block);
	}

	private boolean isTrapdoor(Material block) {
		return block.name().contains("TRAPDOOR") || block.name().contains("TRAP_DOOR");
	}

	private boolean isFenceGate(Material block) {
		return block.name().contains("FENCE_GATE");
	}

	private boolean isDoor(Material block) {
		return block.name().contains("_DOOR") && !block.name().contains("TRAP_DOOR");
	}

	private void toggleDoor(Block bottomHalf, boolean open) {
		if(isDoor(bottomHalf.getType())) {
			Door data = (Door) bottomHalf.getState().getData();
			Block topHalf = bottomHalf.getRelative(BlockFace.UP);

			if(data.isOpen()) {
				// false for left, true for right
				boolean hinge = data.getHinge();

				HashMap<BlockFace, Integer> faceMap = new HashMap<>();
				faceMap.put(BlockFace.EAST, 0);
				faceMap.put(BlockFace.SOUTH, 1);
				faceMap.put(BlockFace.WEST, 2);
				faceMap.put(BlockFace.NORTH, 3);

				int n = faceMap.get(data.getFacing()) + 2;
				BlockFace bn = BlockFace.EAST;

				if(n > 3) n = n % 4;
				for(Map.Entry<BlockFace, Integer> pair : faceMap.entrySet()) {
					if(pair.getValue() == n) bn = pair.getKey();
				}

				switch(bn) {
					case EAST:
						setBlock(bottomHalf, open ? 4 : 0);
						if(hinge) setBlock(topHalf, 9);
						else setBlock(topHalf, 8);
						break;
					case SOUTH:
						setBlock(bottomHalf, open ? 5 : 1);
						if(!hinge) setBlock(topHalf, 8);
						else setBlock(topHalf, 9);
						break;
					case WEST:
						setBlock(bottomHalf, open ? 6 : 2);
						if(hinge) setBlock(topHalf, 9);
						else setBlock(topHalf, 8);
						break;
					case NORTH:
						setBlock(bottomHalf, open ? 7 : 3);
						if(!hinge) setBlock(topHalf, 8);
						else setBlock(topHalf, 9);
						break;
				}
			}
		}
	}

	public void toggleGate(Block block, boolean open) {
		if(isFenceGate(block.getType())) {
			Gate gate = (Gate) block.getState().getData();

			HashMap<BlockFace, Integer> faceMap = new HashMap<>();
			faceMap.put(BlockFace.SOUTH, 0);
			faceMap.put(BlockFace.WEST, 1);
			faceMap.put(BlockFace.NORTH, 2);
			faceMap.put(BlockFace.EAST, 3);

			int n = faceMap.get(gate.getFacing()) - 1;
			BlockFace bn = BlockFace.EAST;

			for(Map.Entry<BlockFace, Integer> pair : faceMap.entrySet()) {
				if(pair.getValue() == n) bn = pair.getKey();
			}

			switch (bn) {
				case SOUTH:
					setBlock(block, open ? 4 : 0);
					break;
				case WEST:
					setBlock(block, open ? 5 : 1);
					break;
				case NORTH:
					setBlock(block, open ? 6 : 2);
					break;
				case EAST:
					setBlock(block, open ? 7 : 3);
					break;
			}
		}
	}

	public void toggleTrapdoor(Block block, boolean open) {
		if(isTrapdoor(block.getType())) {
			TrapDoor trapdoor = (TrapDoor) block.getState().getData();
			boolean top = trapdoor.isInverted();

			switch (trapdoor.getFacing()) {
				case NORTH:
					setBlock(block, open ? top ? 12 : 4 : top ? 8 : 0);
					break;
				case SOUTH:
					setBlock(block, open ? top ? 13 : 5 : top ? 9 : 1);
					break;
				case WEST:
					setBlock(block, open ? top ? 14 : 6 : top ? 10 : 2);
					break;
				case EAST:
					setBlock(block, open ? top ? 15 : 7 : top ? 11 : 3);
					break;
			}
		}
	}

	public void setBlock(Block door, int data) {
		Location l = door.getLocation();

		Material type = door.getType();
		String block = type.name().toLowerCase();

		if(block.equals("wood_door")) block = "wooden_door";
		if(block.equals("trap_door") && Utils.usesVersionBetween("1.4.x", "1.8.x")) block = "trapdoor";

		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "setblock "
				+ l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " "
				+ block + " " + data);
	}
}
