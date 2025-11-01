package com.promedia.sentepos.print;

import java.util.*;

public class Json {
    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }
    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object o) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String s) { sb.append('"').append(escape(s)).append('"'); return; }
        if (o instanceof Number || o instanceof Boolean) { sb.append(o.toString()); return; }
        if (o instanceof Map<?,?> m) {
            sb.append('{'); boolean first=true;
            for (var e: m.entrySet()) {
                if (e.getValue()==null) continue;
                if (!first) sb.append(',');
                first=false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append('"').append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (o instanceof Iterable<?> it) {
            sb.append('['); boolean first=true;
            for (var x: it) { if (!first) sb.append(','); first=false; write(sb, x); }
            sb.append(']');
            return;
        }
        sb.append('"').append(escape(String.valueOf(o))).append('"');
    }
    private static String escape(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
}