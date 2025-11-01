package com.promedia.sentepos.service;

import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.EfrisDAO;
import com.promedia.sentepos.efris.EfrisClient;
import com.promedia.sentepos.efris.EfrisPayloadBuilder;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;

import java.lang.reflect.Method;

public class FiscalService {

    private static final String DEFAULT_ENDPOINT = "https://efris.example/submitInvoice"; // TODO: real URA URL

    /** Build payload → save PENDING → send → mark SENT/FAILED. Returns invoice number on success. */
    public static String fiscalise(long saleId, Sale sale, Payment payment) throws Exception {
        // 1) Build JSON payload
        String payload = EfrisPayloadBuilder.buildInvoicePayload(saleId, sale, payment);

        // 2) Save PENDING
        EfrisDAO.upsertPending(saleId, payload);

        // 3) Resolve endpoint/creds from Business (CamelCase or snake_case fields)
        Object b = BusinessDAO.loadSingle();
        if (b == null) throw new IllegalStateException("Business not configured.");

        String user   = field(b, "efrisUsername", "efris_username");
        String pass   = field(b, "efrisPassword", "efris_password");
        String device = field(b, "efrisDeviceNo", "efris_device_no");
        String endpoint = DEFAULT_ENDPOINT; // or from config

        // 4) Send
        EfrisClient client = new EfrisClient();
        EfrisClient.Result r = client.sendInvoiceJson(payload, endpoint, user, pass, device);

        // 5) Persist (try multiple EfrisDAO signatures without compile dependency)
        if (r.ok) {
            // Preferred: markSent(long, String, String)
            if (!invokeIfExists(EfrisDAO.class, "markSent",
                    new Class<?>[]{long.class, String.class, String.class},
                    new Object[]{saleId, r.invoiceNumber, r.qrBase64})) {

                // Fallback: markSent(long, String)
                invokeIfExists(EfrisDAO.class, "markSent",
                        new Class<?>[]{long.class, String.class},
                        new Object[]{saleId, r.invoiceNumber});

                // Optional: updateQr(long, String) if present
                invokeIfExists(EfrisDAO.class, "updateQr",
                        new Class<?>[]{long.class, String.class},
                        new Object[]{saleId, r.qrBase64});
            }
            return r.invoiceNumber;
        } else {
            // Preferred: markFailed(long, String, String)
            if (!invokeIfExists(EfrisDAO.class, "markFailed",
                    new Class<?>[]{long.class, String.class, String.class},
                    new Object[]{saleId, r.rawResponse, r.error})) {

                // Fallback: markFailed(long, String)
                invokeIfExists(EfrisDAO.class, "markFailed",
                        new Class<?>[]{long.class, String.class},
                        new Object[]{saleId, r.error});
            }
            throw new RuntimeException("Fiscalisation failed: " + r.error);
        }
    }

    // ---------- small reflection helpers ----------
    private static String field(Object o, String... names) {
        if (o == null) return null;
        for (String n : names) {
            try {
                var f = o.getClass().getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(o);
                return v != null ? String.valueOf(v) : null;
            } catch (NoSuchFieldException ignored) { }
            catch (Throwable t) { /* ignore */ }
        }
        return null;
    }

    private static boolean invokeIfExists(Class<?> clazz, String name, Class<?>[] sig, Object[] args) {
        try {
            Method m = clazz.getMethod(name, sig);
            m.invoke(null, args);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            // if the method exists but failed, propagate as runtime
            throw new RuntimeException("Call " + clazz.getSimpleName() + "." + name + " failed: " + t.getMessage(), t);
        }
    }
}