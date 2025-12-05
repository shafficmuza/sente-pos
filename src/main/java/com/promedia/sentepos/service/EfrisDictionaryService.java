package com.promedia.sentepos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.UnitOfMeasureDAO;
import com.promedia.sentepos.model.Business;
import com.promedia.sentepos.efris.EfrisClient;
import com.promedia.sentepos.util.AppLog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EfrisDictionaryService {
    private EfrisDictionaryService(){}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Query EFRIS for Unit-of-Measure dictionary via Offline Enabler
     * and store it into measure_units table.
     *
     * You can call this from an "Admin → Sync EFRIS UOM" button.
     */
    public static void syncUnitOfMeasure() throws Exception {
        Business b = BusinessDAO.loadSingle();
        if (b == null) {
            throw new IllegalStateException("Business not configured.");
        }

        EfrisClient client = new EfrisClient();
        EfrisClient.Result res = client.fetchUnitOfMeasureDictionary(b.efrisDeviceNo);

        if (!res.ok) {
            throw new IllegalStateException("UOM dictionary fetch failed: " + res.error);
        }
        if (res.innerContentJson == null || res.innerContentJson.isBlank()) {
            throw new IllegalStateException("UOM dictionary returned empty content.");
        }

        AppLog.blobInPayloads("uom-sync", "uom-inner-response", res.innerContentJson);

        JsonNode root = MAPPER.readTree(res.innerContentJson);

        // ---- IMPORTANT ----
        // You MUST inspect one real payload from URA and adjust these paths.
        //
        // Example possibilities:
        //   root["records"]            (array)
        //   root["unitOfMeasureList"]  (array)
        //   root["data"]               (array)
        //
        // Each item should have some kind of:
        //   - code: "10"
        //   - name: "Piece"
        //
        // Here we try to be flexible and pick the first array node we find.
        // --------------------

        JsonNode arr = null;
        if (root.isArray()) {
            arr = root;
        } else if (root.has("records") && root.get("records").isArray()) {
            arr = root.get("records");
        } else if (root.has("unitOfMeasureList") && root.get("unitOfMeasureList").isArray()) {
            arr = root.get("unitOfMeasureList");
        } else if (root.has("data") && root.get("data").isArray()) {
            arr = root.get("data");
        }

        if (arr == null || !arr.isArray()) {
            throw new IllegalStateException("Cannot locate UOM array in response – adjust JSON path in EfrisDictionaryService.");
        }

        List<UnitOfMeasureDAO.Row> rows = new ArrayList<>();

        for (Iterator<JsonNode> it = arr.elements(); it.hasNext(); ) {
            JsonNode n = it.next();

            // Try multiple field names for code/name to be safe.
            String code = firstNonEmpty(n,
                    "code", "uomCode", "unitCode", "measureCode", "id", "value");
            String name = firstNonEmpty(n,
                    "name", "uomName", "unitName", "description", "label");

            if (code == null || code.isBlank()) continue;
            if (name == null || name.isBlank()) name = code;

            UnitOfMeasureDAO.Row r = new UnitOfMeasureDAO.Row();
            r.code = code;
            r.name = name;
            r.active = 1;
            rows.add(r);
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException("No UOM rows parsed from EFRIS response – adjust parser.");
        }

        // Replace existing table contents
        UnitOfMeasureDAO.deleteAll();
        UnitOfMeasureDAO.insertAll(rows);

        AppLog.ok("efris", "uom-sync", "Synced " + rows.size() + " unit(s) of measure from EFRIS.");
    }

    private static String firstNonEmpty(JsonNode node, String... names) {
        if (node == null) return null;
        for (String n : names) {
            if (node.hasNonNull(n)) {
                String v = node.get(n).asText("");
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }
}