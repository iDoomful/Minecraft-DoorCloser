package net.tenrem.doorcloser;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static boolean usesVersionBetween(String start, String end) {
        String serverVersion = Bukkit.getServer().getVersion();
        Matcher matcher = Pattern.compile("\\d\\.\\d+\\.?\\d?").matcher(serverVersion);

        if(matcher.find()) serverVersion = matcher.group();

        int[][] patchVersions = new int[16 + 1][];

        patchVersions[4] = new int[] {6, 7};
        patchVersions[5] = new int[] {1, 2};
        patchVersions[6] = new int[] {2, 4};
        patchVersions[7] = new int[] {2, 5, 8, 9, 10};
        patchVersions[8] = new int[] {0, 3, 4, 5, 6, 7, 8};
        patchVersions[9] = new int[] {0, 2, 4};
        patchVersions[10] = new int[] {0, 2};
        patchVersions[11] = new int[] {0, 1, 2};
        patchVersions[12] = new int[] {0, 1, 2};
        patchVersions[13] = new int[] {0, 1, 2};
        patchVersions[14] = new int[] {0, 1, 2, 3, 4};
        patchVersions[15] = new int[] {0, 1, 2};
        patchVersions[16] = new int[] {0, 2};

        for(int browsedMinorVer = 4; browsedMinorVer < patchVersions.length; browsedMinorVer++) {
            for(int j = 0; j < patchVersions[browsedMinorVer].length; j++) {
                int browsedPatchVer = patchVersions[browsedMinorVer][j];

                int minorVerV1 = Integer.parseInt(start.split("\\.")[1]);
                int minorVerV2 = Integer.parseInt(end.split("\\.")[1]);

                final String[] startSplit = start.split("\\.");
                final String[] endSplit = end.split("\\.");

                int patchVerStart = Integer.parseInt(startSplit.length == 2 || startSplit[2].equals("x") ? "0" : startSplit[2]);
                int patchVerEnd = Integer.parseInt(endSplit.length == 2 || endSplit[2].equals("x") ? "0" : endSplit[2]);

                int lastPatch = patchVersions[browsedMinorVer][patchVersions[browsedMinorVer].length - 1];

                boolean limitsOtherVersionsPatches = (browsedMinorVer == minorVerV1) && !(patchVerStart <= browsedPatchVer && patchVerEnd <= lastPatch);

                if(browsedPatchVer != 0) {
                    if((browsedMinorVer >= minorVerV1) && (browsedMinorVer <= minorVerV2)) {
                        if(limitsOtherVersionsPatches) continue;
                        String match = "1." + browsedMinorVer + "." + browsedPatchVer;
                        if(serverVersion.equals(match)) return true;
                    }
                } else {
                    if(limitsOtherVersionsPatches) continue;

                    if(browsedMinorVer >= minorVerV1 && browsedMinorVer <= minorVerV2) {
                        String match = "1." + browsedMinorVer;
                        if(serverVersion.equals(match)) return true;
                    }
                }
            }
        }

        return false;
    }
}
