package orange.wz.mcp.support;

import orange.wz.mcp.dto.NodeSummary;
import orange.wz.mcp.resolve.NodePathResolver;
import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFolder;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzCanvasProperty;
import orange.wz.provider.properties.WzPngFormat;
import orange.wz.provider.properties.WzStringProperty;
import orange.wz.provider.properties.WzUOLProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cross-resource consistency helpers for MapleStory packs
 * (Item / Character / String / Mob / Npc … and UOL / _outlink).
 */
public final class ResourceLinkAnalyzer {
    private ResourceLinkAnalyzer() {
    }

    public static boolean isDirtyRoot(WzObject root) {
        if (root == null) {
            return false;
        }
        if (root.isTempChanged()) {
            return true;
        }
        if (root instanceof WzImage image) {
            return image.isChanged();
        }
        if (root instanceof WzDirectory dir) {
            if (dir.isTempChanged()) {
                return true;
            }
            for (WzImage image : dir.getImages()) {
                if (image.isChanged() || image.isTempChanged()) {
                    return true;
                }
            }
            for (WzDirectory sub : dir.getDirectories()) {
                if (isDirtyRoot(sub)) {
                    return true;
                }
            }
            return false;
        }
        if (root instanceof WzFolder folder) {
            for (WzObject child : folder.getChildren()) {
                if (isDirtyRoot(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Map<String, Object>> collectDirtyRoots(List<WzObject> roots) {
        List<Map<String, Object>> dirty = new ArrayList<>();
        if (roots == null) {
            return dirty;
        }
        for (WzObject root : roots) {
            if (!isDirtyRoot(root)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", root.getName());
            row.put("rootPath", NodePathResolver.rootPathOf(root));
            row.put("type", root.getType() == null ? null : root.getType().name());
            row.put("tempChanged", root.isTempChanged());
            dirty.add(row);
        }
        return dirty;
    }

    /**
     * Locate id-like nodes under loaded roots and report companion presence + broken UOL/_outlink.
     */
    public static Map<String, Object> analyzeIds(
            List<WzObject> roots,
            List<String> ids,
            boolean autoParse,
            int maxUolChecks
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int uolChecked = 0;
        int uolBroken = 0;
        int missingCompanions = 0;

        if (ids != null) {
            for (String rawId : ids) {
                if (rawId == null || rawId.isBlank()) {
                    continue;
                }
                String id = rawId.trim();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                List<NodeSummary> hits = new ArrayList<>();
                for (WzObject root : roots) {
                    findNameHits(root, id, autoParse, hits, 32);
                }
                row.put("hits", hits);
                row.put("hitCount", hits.size());

                Set<String> categories = categorizeHits(hits);
                row.put("categories", categories);
                List<String> missing = expectedMissing(categories, id);
                row.put("missingCompanions", missing);
                if (!missing.isEmpty()) {
                    missingCompanions++;
                }

                List<Map<String, Object>> brokenLinks = new ArrayList<>();
                for (NodeSummary hit : hits) {
                    if (uolChecked >= maxUolChecks) {
                        break;
                    }
                    WzObject node = resolveQuiet(roots, hit.rootPath(), hit.nodePath(), autoParse);
                    if (node == null) {
                        continue;
                    }
                    List<Map<String, Object>> broken = new ArrayList<>();
                    collectBrokenLinks(node, autoParse, broken, maxUolChecks - uolChecked);
                    uolChecked += broken.size() + 1;
                    for (Map<String, Object> b : broken) {
                        brokenLinks.add(b);
                        uolBroken++;
                    }
                }
                row.put("brokenLinks", brokenLinks);
                items.add(row);
            }
        }

        result.put("ids", items);
        result.put("missingCompanionCount", missingCompanions);
        result.put("uolBrokenCount", uolBroken);
        result.put("ok", missingCompanions == 0 && uolBroken == 0);
        return result;
    }

    public static Map<String, Object> scanCanvasFormats(
            WzObject start,
            boolean autoParse,
            int maxReport,
            Set<String> flagFormats
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> flagged = new ArrayList<>();
        int[] counters = new int[WzPngFormat.values().length + 1];
        walkCanvas(start, autoParse, flagged, counters, maxReport, flagFormats);
        Map<String, Integer> byFormat = new LinkedHashMap<>();
        for (WzPngFormat fmt : WzPngFormat.values()) {
            int c = counters[fmt.ordinal()];
            if (c > 0) {
                byFormat.put(fmt.name(), c);
            }
        }
        result.put("byFormat", byFormat);
        result.put("flagged", flagged);
        result.put("flaggedCount", flagged.size());
        result.put("ok", flagged.isEmpty());
        return result;
    }

    private static void walkCanvas(
            WzObject current,
            boolean autoParse,
            List<Map<String, Object>> flagged,
            int[] counters,
            int maxReport,
            Set<String> flagFormats
    ) {
        if (current instanceof WzCanvasProperty canvas) {
            WzPngFormat fmt = canvas.getFormat();
            if (fmt != null) {
                counters[fmt.ordinal()]++;
                if (flagFormats.contains(fmt.name()) && flagged.size() < maxReport) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rootPath", NodePathResolver.rootPathOf(canvas));
                    row.put("nodePath", NodePathResolver.nodePathOf(canvas));
                    row.put("pngFormat", fmt.name());
                    row.put("width", canvas.getWidth());
                    row.put("height", canvas.getHeight());
                    flagged.add(row);
                }
            }
        }
        for (WzObject child : children(current, autoParse)) {
            walkCanvas(child, autoParse, flagged, counters, maxReport, flagFormats);
        }
    }

    private static void findNameHits(WzObject current, String id, boolean autoParse, List<NodeSummary> hits, int limit) {
        if (hits.size() >= limit) {
            return;
        }
        String name = current.getName();
        if (name != null) {
            String n = name.toLowerCase(Locale.ROOT);
            String needle = id.toLowerCase(Locale.ROOT);
            if (n.equals(needle) || n.equals(needle + ".img") || n.contains(needle)) {
                // Prefer exact / img-exact style hits
                if (n.equals(needle) || n.equals(needle + ".img") || n.startsWith(needle)) {
                    hits.add(NodeSummary.from(current));
                }
            }
        }
        for (WzObject child : children(current, autoParse)) {
            findNameHits(child, id, autoParse, hits, limit);
            if (hits.size() >= limit) {
                return;
            }
        }
    }

    private static Set<String> categorizeHits(List<NodeSummary> hits) {
        java.util.LinkedHashSet<String> cats = new java.util.LinkedHashSet<>();
        for (NodeSummary hit : hits) {
            String path = (hit.rootPath() + "/" + hit.nodePath()).toLowerCase(Locale.ROOT).replace('\\', '/');
            if (path.contains("/item") || path.contains("item.wz") || path.contains("/consume") || path.contains("/eqp")
                    || path.contains("/equip") || path.contains("/cash") || path.contains("/etc")) {
                cats.add("Item");
            }
            if (path.contains("/character") || path.contains("character.wz") || path.contains("/weapon")
                    || path.contains("/cap") || path.contains("/coat") || path.contains("/glove")) {
                cats.add("Character");
            }
            if (path.contains("/string") || path.contains("string.wz") || path.contains("consume.img")
                    || path.contains("eqp.img") || path.contains("mob.img") || path.contains("npc.img")
                    || path.contains("map.img") || path.contains("skill.img")) {
                cats.add("String");
            }
            if (path.contains("/mob") || path.contains("mob.wz")) {
                cats.add("Mob");
            }
            if (path.contains("/npc") || path.contains("npc.wz")) {
                cats.add("Npc");
            }
            if (path.contains("/map") || path.contains("map.wz")) {
                cats.add("Map");
            }
            if (path.contains("/skill") || path.contains("skill.wz")) {
                cats.add("Skill");
            }
        }
        return cats;
    }

    private static List<String> expectedMissing(Set<String> categories, String id) {
        List<String> missing = new ArrayList<>();
        // Equip-like ids often need Character + Item + String
        boolean looksEquip = id.length() >= 5 && (id.startsWith("01") || id.startsWith("1"));
        boolean looksConsume = id.startsWith("02") || id.startsWith("2");
        boolean looksMob = id.length() >= 7 && id.chars().allMatch(Character::isDigit) && !looksEquip && !looksConsume;

        if (looksEquip) {
            if (!categories.contains("Character")) missing.add("Character");
            if (!categories.contains("Item")) missing.add("Item");
            if (!categories.contains("String")) missing.add("String");
        } else if (looksConsume) {
            if (!categories.contains("Item")) missing.add("Item");
            if (!categories.contains("String")) missing.add("String");
        } else if (looksMob) {
            if (!categories.contains("Mob")) missing.add("Mob");
            if (!categories.contains("String")) missing.add("String");
        }
        return missing;
    }

    private static void collectBrokenLinks(
            WzObject current,
            boolean autoParse,
            List<Map<String, Object>> broken,
            int remaining
    ) {
        if (remaining <= 0) {
            return;
        }
        if (current instanceof WzUOLProperty uol) {
            if (!uolResolves(uol)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "uol");
                row.put("rootPath", NodePathResolver.rootPathOf(uol));
                row.put("nodePath", NodePathResolver.nodePathOf(uol));
                row.put("value", uol.getValue());
                broken.add(row);
            }
        } else if (current instanceof WzStringProperty str && "_outlink".equalsIgnoreCase(str.getName())) {
            // _outlink is resolved by client/tooling against WZ tree; mark empty targets
            if (str.getValue() == null || str.getValue().isBlank()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "_outlink");
                row.put("rootPath", NodePathResolver.rootPathOf(str));
                row.put("nodePath", NodePathResolver.nodePathOf(str));
                row.put("value", str.getValue());
                broken.add(row);
            }
        }
        for (WzObject child : children(current, autoParse)) {
            if (broken.size() >= remaining) {
                return;
            }
            collectBrokenLinks(child, autoParse, broken, remaining - broken.size());
        }
    }

    private static boolean uolResolves(WzUOLProperty uol) {
        String value = uol.getValue();
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return uol.getUolTarget() != null;
        } catch (Throwable ignored) {
            // Relative UOL that escapes current image often throws; treat as unresolved.
            return false;
        }
    }

    private static WzObject resolveQuiet(List<WzObject> roots, String rootPath, String nodePath, boolean autoParse) {
        try {
            return new NodePathResolver().resolveFromRoots(
                    roots,
                    new orange.wz.mcp.dto.NodeReference(rootPath, nodePath),
                    autoParse
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static List<WzObject> children(WzObject parent, boolean autoParse) {
        if (parent instanceof WzFolder folder) {
            return folder.getChildren();
        }
        if (parent instanceof WzDirectory dir) {
            if (dir.isWzFile() && autoParse && dir.getWzFile() != null) {
                dir.getWzFile().parse();
            }
            return dir.getChildren();
        }
        if (parent instanceof WzImage image) {
            if (autoParse) {
                image.parse();
            }
            return new ArrayList<>(image.getChildren());
        }
        if (parent instanceof WzImageProperty prop && prop.isListProperty()) {
            return new ArrayList<>(prop.getChildren());
        }
        return List.of();
    }
}
