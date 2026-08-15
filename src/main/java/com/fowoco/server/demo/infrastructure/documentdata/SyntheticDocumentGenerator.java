package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

final class SyntheticDocumentGenerator {

    private static final String GOLD_WORKER_PORTRAIT = "/demo-data/nguyen-van-an-portrait.png";
    private static final DateTimeFormatter PASSPORT_DATE =
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private static final List<String> IDENTITY_LINES = List.of(
            "DISPLAY NAME: NGUYEN VAN AN",
            "NATIONALITY: VIET NAM (VN)",
            "PREFERRED LANGUAGE: vi",
            "VISA: E-9",
            "SYNTHETIC REFERENCE: DEMO-VN-FOWOCO-0001",
            "DATE OF BIRTH: 1995-04-12 (SYNTHETIC)",
            "ADDRESS: DEMO ADDRESS 14, SAMPLE-RO, FOWOCO"
    );

    byte[] generate(DemoDocumentFixture fixture, LocalDate issueDate, LocalDate expiryDate) {
        return switch (fixture.format()) {
            case PNG -> image(fixture, issueDate, expiryDate, "png");
            case JPEG -> image(fixture, issueDate, expiryDate, "jpg");
            case PDF -> pdf(fixture);
            case HWP -> hwp(fixture);
            case HWPX -> hwpx(fixture);
            case NONE -> throw new IllegalArgumentException("missing fixtures do not have file content");
        };
    }

    private byte[] image(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate,
            String format
    ) {
        if (fixture.documentId().equals(DemoDocumentFixtureCatalog.PASSPORT_BIO_DOCUMENT_ID)) {
            return passportBiographicalPage(issueDate, expiryDate, format);
        }
        return genericImage(fixture, format);
    }

    private byte[] passportBiographicalPage(
            LocalDate issueDate,
            LocalDate expiryDate,
            String format
    ) {
        int width = 1400;
        int height = 900;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            graphics.setColor(new Color(246, 242, 226));
            graphics.fillRect(0, 0, width, height);

            graphics.setColor(new Color(25, 55, 94));
            graphics.fillRect(0, 0, width, 120);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            graphics.drawString("FOWOCO QUALITY ASSURANCE FIXTURE", 48, 46);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
            graphics.drawString("DEMO PASSPORT / BIOGRAPHICAL PAGE", 48, 91);

            graphics.setColor(new Color(188, 53, 44));
            graphics.fillRect(0, 120, width, 54);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            graphics.drawString("SAMPLE ONLY - NOT A TRAVEL DOCUMENT - NOT FOR OFFICIAL SUBMISSION", 48, 156);

            drawPortrait(graphics);

            graphics.setColor(new Color(25, 55, 94));
            graphics.setStroke(new BasicStroke(3f));
            graphics.drawRoundRect(42, 198, 346, 470, 18, 18);

            drawField(graphics, "TYPE", "SAMPLE", 430, 235);
            drawField(graphics, "ISSUING CODE", "XDM (FICTIONAL)", 690, 235);
            drawField(graphics, "DOCUMENT NO.", "DEMO-0001-NOT-VALID", 1010, 235);
            drawField(graphics, "SURNAME", "NGUYEN", 430, 325);
            drawField(graphics, "GIVEN NAMES", "VAN AN", 840, 325);
            drawField(graphics, "NATIONALITY", "VIET NAM (VN)", 430, 415);
            drawField(graphics, "DATE OF BIRTH", "12 APR 1995 / SYNTHETIC", 840, 415);
            drawField(graphics, "SEX", "M", 430, 505);
            drawField(graphics, "PLACE OF BIRTH", "DEMO CITY", 610, 505);
            drawField(graphics, "VISA / STAY", "E-9", 1010, 505);
            drawField(graphics, "DATE OF ISSUE", passportDate(issueDate), 430, 595);
            drawField(graphics, "DATE OF EXPIRY", passportDate(expiryDate), 740, 595);
            drawField(graphics, "AUTHORITY", "FOWOCO QA FIXTURE", 1050, 595);

            graphics.setColor(new Color(71, 85, 105));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            graphics.drawString("Holder label: NGUYEN VAN AN / synthetic identity", 430, 660);

            graphics.setColor(new Color(25, 55, 94));
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawRoundRect(42, 704, width - 84, 142, 16, 16);
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
            graphics.drawString("P<XDMNGUYEN<<VAN<AN<<DEMO<SAMPLE<ONLY<<<<", 70, 760);
            graphics.drawString("NOTVALID<<FOWOCO<QA<FIXTURE<<NO<TRAVEL<USE", 70, 812);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            graphics.setColor(new Color(188, 53, 44));
            graphics.drawString("INTENTIONALLY INVALID MACHINE-READABLE SAMPLE", 925, 836);

            AffineTransform originalTransform = graphics.getTransform();
            var originalComposite = graphics.getComposite();
            graphics.rotate(Math.toRadians(-18), 760, 450);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
            graphics.setColor(new Color(188, 53, 44));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 150));
            graphics.drawString("DEMO SAMPLE", 300, 500);
            graphics.setTransform(originalTransform);
            graphics.setComposite(originalComposite);
        } finally {
            graphics.dispose();
        }
        return encodeImage(image, format);
    }

    private byte[] genericImage(DemoDocumentFixture fixture, String format) {
        int width = 1200;
        int height = 760;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            graphics.setColor(new Color(248, 250, 252));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(185, 28, 28));
            graphics.fillRect(0, 0, width, 92);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            graphics.drawString("DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION", 48, 58);

            graphics.setColor(new Color(15, 23, 42));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
            graphics.drawString(fixture.title(), 58, 164);
            graphics.setStroke(new BasicStroke(2f));
            graphics.setColor(new Color(148, 163, 184));
            graphics.drawRoundRect(48, 195, width - 96, height - 255, 18, 18);

            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 23));
            graphics.setColor(new Color(30, 41, 59));
            int y = 250;
            for (String line : IDENTITY_LINES) {
                graphics.drawString(line, 82, y);
                y += 55;
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            graphics.setColor(new Color(185, 28, 28));
            graphics.drawString("SYNTHETIC DATA ONLY / NO GOVERNMENT SECURITY FEATURES", 82, height - 82);
        } finally {
            graphics.dispose();
        }
        return encodeImage(image, format);
    }

    private void drawPortrait(Graphics2D graphics) {
        try (var input = SyntheticDocumentGenerator.class.getResourceAsStream(GOLD_WORKER_PORTRAIT)) {
            BufferedImage portrait = ImageIO.read(Objects.requireNonNull(
                    input,
                    "missing synthetic Gold Worker portrait: " + GOLD_WORKER_PORTRAIT
            ));
            graphics.drawImage(portrait, 58, 214, 314, 392, null);
            graphics.setColor(new Color(30, 41, 59));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            graphics.drawString("SYNTHETIC PORTRAIT", 105, 642);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to load synthetic Gold Worker portrait", exception);
        }
    }

    private void drawField(Graphics2D graphics, String label, String value, int x, int y) {
        graphics.setColor(new Color(71, 85, 105));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        graphics.drawString(label, x, y);
        graphics.setColor(new Color(15, 23, 42));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        graphics.drawString(value, x, y + 34);
    }

    private String passportDate(LocalDate value) {
        return value == null ? "NOT SET" : PASSPORT_DATE.format(value).toUpperCase(Locale.ENGLISH);
    }

    private void enableHighQualityRendering(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private byte[] encodeImage(BufferedImage image, String format) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("ImageIO writer is unavailable: " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to generate synthetic image", exception);
        }
    }

    private byte[] pdf(DemoDocumentFixture fixture) {
        List<String> lines = new ArrayList<>();
        lines.add("DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION");
        lines.add(fixture.title());
        lines.addAll(IDENTITY_LINES);
        lines.add("NO GOVERNMENT LOGO, SECURITY PATTERN, OR OFFICIAL SIGNATURE");

        StringBuilder stream = new StringBuilder("BT\n/F1 12 Tf\n50 790 Td\n");
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                stream.append("0 -42 Td\n");
            }
            stream.append('(').append(pdfEscape(lines.get(index))).append(") Tj\n");
        }
        stream.append("ET\n");
        byte[] streamBytes = stream.toString().getBytes(StandardCharsets.US_ASCII);

        List<byte[]> objects = List.of(
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"),
                concat(ascii("<< /Length " + streamBytes.length + " >>\nstream\n"),
                        streamBytes, ascii("endstream")),
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, ascii("%PDF-1.4\n% FOWOCO synthetic fixture\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            write(output, ascii((index + 1) + " 0 obj\n"));
            write(output, objects.get(index));
            write(output, ascii("\nendobj\n"));
        }
        int xref = output.size();
        write(output, ascii("xref\n0 " + (objects.size() + 1) + "\n"));
        write(output, ascii("0000000000 65535 f \n"));
        offsets.forEach(offset -> write(output, ascii("%010d 00000 n \n".formatted(offset))));
        write(output, ascii("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n"
                + "startxref\n" + xref + "\n%%EOF\n"));
        return output.toByteArray();
    }

    private byte[] hwp(DemoDocumentFixture fixture) {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] header = new byte[256];
            byte[] signature = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(signature, 0, header, 0, signature.length);
            fileSystem.getRoot().createDocument("FileHeader", new ByteArrayInputStream(header));
            var body = fileSystem.getRoot().createDirectory("BodyText");
            String text = fixture.title() + "\nDEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION\n"
                    + String.join("\n", IDENTITY_LINES);
            body.createDocument("Section0", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            fileSystem.writeFilesystem(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to generate synthetic HWP container", exception);
        }
    }

    private byte[] hwpx(DemoDocumentFixture fixture) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            byte[] mime = "application/hwp+zip".getBytes(StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(mime);
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            mimeEntry.setSize(mime.length);
            mimeEntry.setCompressedSize(mime.length);
            mimeEntry.setCrc(crc.getValue());
            mimeEntry.setTime(0L);
            zip.putNextEntry(mimeEntry);
            zip.write(mime);
            zip.closeEntry();

            addZipEntry(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="Contents/content.hpf" media-type="application/xml"/></rootfiles>
                    </container>
                    """);
            addZipEntry(zip, "Contents/content.hpf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.hancom.co.kr/hwpml/2011/package">
                      <metadata><title>FOWOCO synthetic document fixture</title></metadata>
                    </package>
                    """);
            addZipEntry(zip, "Contents/header.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <hh:head xmlns:hh="http://www.hancom.co.kr/hwpml/2011/head" version="1.0"/>
                    """);
            String body = fixture.title() + " | DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION | "
                    + String.join(" | ", IDENTITY_LINES);
            addZipEntry(zip, "Contents/section0.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <hs:sec xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section">
                      <hp:p xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph">
                        <hp:run><hp:t>%s</hp:t></hp:run>
                      </hp:p>
                    </hs:sec>
                    """.formatted(xmlEscape(body)));
            addZipEntry(zip, "Preview/PrvText.txt", body);
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to generate synthetic HWPX", exception);
        }
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] concat(byte[]... chunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            write(output, chunk);
        }
        return output.toByteArray();
    }

    private void write(ByteArrayOutputStream output, byte[] bytes) {
        output.writeBytes(bytes);
    }
}
