package com.mercadototal.personal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Genera un archivo .xlsx sencillo (una hoja) sin librerías externas. */
public class XlsxWriter {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    public static byte[] crear(String hoja, List<Object[]> filas) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bos);

        poner(z, "[Content_Types].xml", XML
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "</Types>");

        poner(z, "_rels/.rels", XML
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>");

        poner(z, "xl/workbook.xml", XML
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"" + REL + "\">"
            + "<sheets><sheet name=\"" + esc(hoja) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");

        poner(z, "xl/_rels/workbook.xml.rels", XML
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + REL + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "</Relationships>");

        StringBuilder sb = new StringBuilder(XML);
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < filas.size(); r++) {
            Object[] fila = filas.get(r);
            sb.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < fila.length; c++) {
                String ref = columna(c) + (r + 1);
                Object v = fila[c];
                if (v instanceof Number) {
                    sb.append("<c r=\"").append(ref).append("\"><v>")
                      .append(BigDecimal.valueOf(((Number) v).doubleValue()).toPlainString())
                      .append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                      .append(esc(v == null ? "" : v.toString()))
                      .append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        poner(z, "xl/worksheets/sheet1.xml", sb.toString());

        z.close();
        return bos.toByteArray();
    }

    private static void poner(ZipOutputStream z, String nombre, String contenido) throws IOException {
        z.putNextEntry(new ZipEntry(nombre));
        z.write(contenido.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static String columna(int c) {
        String s = "";
        c++;
        while (c > 0) {
            int m = (c - 1) % 26;
            s = (char) ('A' + m) + s;
            c = (c - 1) / 26;
        }
        return s;
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '&') b.append("&amp;");
            else if (ch == '<') b.append("&lt;");
            else if (ch == '>') b.append("&gt;");
            else if (ch == '"') b.append("&quot;");
            else if (ch >= 0x20 || ch == '\n' || ch == '\t') b.append(ch);
        }
        return b.toString();
    }
}
