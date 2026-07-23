import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the dummy PDF files used by this POC: the main Purchase Order
 * sample and the three mock SharePoint supporting documents. Produces
 * minimal but fully valid, non-copyrighted PDF 1.4 files by hand (no
 * external PDF library needed).
 *
 * Run from the project root (sharepoint-docusign-poc/):
 *   java scripts/GenerateSamplePdfs.java
 *
 * Creates:
 *   sample-files/Purchase-Order-4500000105.pdf
 *   src/main/resources/mock-sharepoint/4500000105/REV-02/Technical-Specification.pdf
 *   src/main/resources/mock-sharepoint/4500000105/REV-02/Commercial-Conditions.pdf
 *   src/main/resources/mock-sharepoint/4500000105/REV-02/Safety-Requirements.pdf
 */
public class GenerateSamplePdfs {

    public static void main(String[] args) throws IOException {
        writePdf(
                Path.of("sample-files", "Purchase-Order-4500000105.pdf"),
                "Purchase Order",
                List.of("Purchase Order 4500000105", "Revision 02", "/vendor-signature/"));

        Path mockFolder = Path.of("src", "main", "resources", "mock-sharepoint", "4500000105", "REV-02");
        writePdf(
                mockFolder.resolve("Technical-Specification.pdf"),
                "Technical Specification",
                List.of("Technical Specification", "PO 4500000105 - Revision 02", "Sample supporting document for POC purposes."));
        writePdf(
                mockFolder.resolve("Commercial-Conditions.pdf"),
                "Commercial Conditions",
                List.of("Commercial Conditions", "PO 4500000105 - Revision 02", "Sample supporting document for POC purposes."));
        writePdf(
                mockFolder.resolve("Safety-Requirements.pdf"),
                "Safety Requirements",
                List.of("Safety Requirements", "PO 4500000105 - Revision 02", "Sample supporting document for POC purposes."));

        System.out.println("Generated sample PDFs.");
    }

    private static void writePdf(Path path, String heading, List<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, buildPdf(heading, lines));
        System.out.println("Wrote " + path.toAbsolutePath());
    }

    private static byte[] buildPdf(String heading, List<String> bodyLines) throws IOException {
        List<String> allLines = new ArrayList<>();
        allLines.add(heading);
        allLines.addAll(bodyLines);

        StringBuilder content = new StringBuilder();
        int y = 720;
        boolean first = true;
        for (String line : allLines) {
            int fontSize = first ? 20 : 14;
            content.append("BT /F1 ").append(fontSize).append(" Tf 72 ").append(y)
                    .append(" Td (").append(escape(line)).append(") Tj ET\n");
            y -= first ? 40 : 28;
            first = false;
        }
        byte[] streamBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        buffer.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));

        offsets.add(0); // object 0 is the free-list head, not written
        writeTrackedObject(buffer, offsets, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        writeTrackedObject(buffer, offsets, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        writeTrackedObject(buffer, offsets,
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> "
                        + "/MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n");
        writeTrackedObject(buffer, offsets, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

        offsets.add(buffer.size());
        buffer.write(("5 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
        buffer.write(streamBytes);
        buffer.write("endstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        int xrefOffset = buffer.size();
        int objectCount = 6;
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(objectCount).append("\n");
        xref.append("0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            xref.append(String.format("%010d 00000 n %n", offsets.get(i)));
        }
        xref.append("trailer\n<< /Size ").append(objectCount).append(" /Root 1 0 R >>\n");
        xref.append("startxref\n").append(xrefOffset).append("\n%%EOF");
        buffer.write(xref.toString().getBytes(StandardCharsets.ISO_8859_1));

        return buffer.toByteArray();
    }

    private static void writeTrackedObject(ByteArrayOutputStream buffer, List<Integer> offsets, String text) throws IOException {
        offsets.add(buffer.size());
        buffer.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
