package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.PassportIdentity;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

final class SyntheticDocumentGenerator {

    private static final String GOLD_WORKER_PORTRAIT = "/demo-data/nguyen-van-an-portrait.png";
    private static final DateTimeFormatter PASSPORT_DATE =
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    byte[] generate(DemoDocumentFixture fixture, LocalDate issueDate, LocalDate expiryDate) {
        return switch (fixture.format()) {
            case PNG -> image(fixture, issueDate, expiryDate, "png");
            case JPEG -> image(fixture, issueDate, expiryDate, "jpg");
            case PDF -> pdf(fixture, issueDate, expiryDate);
            case HWP -> hwp(fixture, issueDate, expiryDate);
            case HWPX -> hwpx(fixture, issueDate, expiryDate);
            case NONE -> throw new IllegalArgumentException("missing fixtures do not have file content");
        };
    }

    private byte[] image(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate,
            String format
    ) {
        if (fixture.passportIdentity() != null) {
            if (fixture.documentType() == com.fowoco.server.worker.domain.DocumentType.PASSPORT_COPY) {
                return passportBiographicalPage(fixture, issueDate, expiryDate, format);
            }
        }
        return genericImage(fixture, issueDate, expiryDate, format);
    }

    private byte[] passportBiographicalPage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate,
            String format
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        boolean goldPassport = fixture.documentId().equals(DemoDocumentFixtureCatalog.PASSPORT_BIO_DOCUMENT_ID);
        int width = 1400;
        int height = 980;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            graphics.setPaint(new GradientPaint(
                    0, 0, new Color(239, 248, 249),
                    width, height, new Color(252, 234, 224)
            ));
            graphics.fillRect(0, 0, width, height);
            drawPassportBackdrop(graphics, width, height);

            graphics.setColor(new Color(20, 61, 104));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
            graphics.drawString("DEMO PASSPORT", 56, 72);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            graphics.drawString("FOWOCO SYNTHETIC IDENTITY PAGE", 450, 72);
            drawDemoBadge(graphics);

            graphics.setColor(new Color(20, 61, 104, 120));
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawLine(55, 108, width - 55, 108);

            drawPortrait(graphics, fixture);

            graphics.setColor(new Color(25, 55, 94));
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawRoundRect(52, 160, 350, 492, 14, 14);

            drawField(graphics, "Type", "SAMPLE", 440, 145);
            drawField(graphics, "Fictional code", "XDM", 645, 145);
            drawField(graphics, "Document No.", identity.documentNumber(), 875, 145);
            drawField(graphics, "Surname", identity.surname(), 440, 230);
            drawField(graphics, "Given names", identity.givenNames(), 780, 230);
            drawField(graphics, "Nationality",
                    "%s (%s)".formatted(identity.nationality(), identity.nationalityCode()), 440, 325);
            drawField(graphics, "Date of birth", passportDate(identity.birthDate()), 800, 325);
            drawField(graphics, "Sex", identity.sex(), 1180, 325);
            drawField(graphics, "Place of birth", "DEMO CITY", 440, 420);
            drawField(graphics, "Visa / Stay", identity.visaType(), 735, 420);
            drawField(graphics, "Fictional authority", "FOWOCO QA LAB", 940, 420);
            drawField(graphics, "Date of issue", passportDate(issueDate), 440, 515);
            drawField(graphics, "Date of expiry", passportDate(expiryDate), 760, 515);
            drawField(graphics, "Document status", "DEMO · NOT VALID", 1080, 515);

            graphics.setColor(new Color(71, 85, 105));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            String holderLabel = goldPassport
                    ? "SYNTHETIC HOLDER · NGUYEN VAN AN · DEMO PROFILE"
                    : "SYNTHETIC HOLDER · %s · PROFILE %02d".formatted(
                            identity.englishName(), identity.portraitSeed()
                    );
            graphics.drawString(holderLabel, 440, 650);

            graphics.setColor(new Color(20, 61, 104, 90));
            graphics.drawRoundRect(1185, 210, 140, 100, 16, 16);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
            graphics.drawString("D", 1234, 274);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            graphics.drawString("DEMO MARK", 1219, 296);

            graphics.setColor(new Color(250, 248, 241));
            graphics.fillRect(0, 705, width, 225);
            graphics.setColor(new Color(20, 61, 104, 120));
            graphics.drawLine(42, 705, width - 42, 705);
            graphics.setColor(new Color(17, 38, 66));
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 31));
            String mrzLine1 = goldPassport
                    ? "P<XDMNGUYEN<<VAN<AN<<DEMO<SAMPLE<ONLY<<<<"
                    : "P<XDM" + mrz(identity.surname()) + "<<" + mrz(identity.givenNames())
                            + "<<DEMO<SAMPLE<ONLY";
            String mrzLine2 = goldPassport
                    ? "NOTVALID<<FOWOCO<QA<FIXTURE<<NO<TRAVEL<USE"
                    : "NOTVALID<" + identity.nationalityCode() + "<PROFILE<"
                            + "%02d".formatted(identity.portraitSeed()) + "<NO<TRAVEL<USE";
            graphics.drawString(fitMrz(mrzLine1), 54, 785);
            graphics.drawString(fitMrz(mrzLine2), 54, 842);

            graphics.setColor(new Color(176, 47, 43));
            graphics.fillRect(0, 930, width, 50);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            graphics.drawString(
                    "DEMO SAMPLE ONLY · NOT A TRAVEL DOCUMENT · NO GOVERNMENT SECURITY FEATURES",
                    315,
                    962
            );

            AffineTransform originalTransform = graphics.getTransform();
            var originalComposite = graphics.getComposite();
            graphics.rotate(Math.toRadians(-16), 760, 420);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
            graphics.setColor(new Color(188, 53, 44));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 125));
            graphics.drawString("DEMO · NOT VALID", 220, 485);
            graphics.setTransform(originalTransform);
            graphics.setComposite(originalComposite);
        } finally {
            graphics.dispose();
        }
        return encodeImage(image, format);
    }

    private byte[] genericImage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate,
            String format
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        int width = 1200;
        int height = 760;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            graphics.setColor(new Color(248, 250, 252));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(20, 61, 104));
            graphics.fillRect(0, 0, width, 92);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            graphics.drawString("DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION", 48, 58);

            graphics.setColor(new Color(15, 23, 42));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            graphics.drawString(documentHeading(fixture), 58, 154);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            graphics.setColor(new Color(71, 85, 105));
            graphics.drawString(fixture.title(), 58, 184);
            graphics.setStroke(new BasicStroke(2f));
            graphics.setColor(new Color(148, 163, 184));
            graphics.drawRoundRect(48, 205, width - 96, height - 275, 18, 18);

            drawCompactField(graphics, "Holder", identity.englishName(), 82, 245);
            drawCompactField(graphics, "Nationality", identity.nationality()
                    + " (" + identity.nationalityCode() + ")", 610, 245);
            drawCompactField(graphics, "Date of birth", identity.birthDate().toString(), 82, 335);
            drawCompactField(graphics, "Visa / Stay", identity.visaType(), 610, 335);
            drawCompactField(graphics, "Document type", fixture.documentType().name(), 82, 425);
            drawCompactField(graphics, "Submission status", fixture.status().name(), 610, 425);
            drawCompactField(graphics, "Issue date", metadataDate(issueDate), 82, 515);
            drawCompactField(graphics, "Expiry date", metadataDate(expiryDate), 610, 515);
            drawCompactField(graphics, documentNumberLabel(fixture), documentNumber(fixture), 82, 605);
            drawCompactField(graphics, "Worker document ID", fixture.documentId().toString(), 610, 605);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            graphics.setColor(new Color(185, 28, 28));
            graphics.drawString("SYNTHETIC DATA ONLY / NOT VALID / NO OFFICIAL SECURITY FEATURES", 82, height - 48);
        } finally {
            graphics.dispose();
        }
        return encodeImage(image, format);
    }

    private void drawPortrait(Graphics2D graphics, DemoDocumentFixture fixture) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        String portraitResource = identity.portraitSeed() == 6
                ? GOLD_WORKER_PORTRAIT
                : "/demo-data/passport-portraits/worker-%02d.png".formatted(identity.portraitSeed());
        try (var input = SyntheticDocumentGenerator.class.getResourceAsStream(portraitResource)) {
            BufferedImage portrait = ImageIO.read(Objects.requireNonNull(
                    input,
                    "missing synthetic passport portrait: " + portraitResource
            ));
            drawCroppedPortrait(graphics, portrait, 67, 175, 320, 420);
            graphics.setColor(new Color(30, 41, 59));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            graphics.drawString(
                    "SYNTHETIC PORTRAIT / PROFILE %02d".formatted(identity.portraitSeed()),
                    105,
                    628
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to load synthetic passport portrait", exception);
        }
    }

    private void drawCroppedPortrait(
            Graphics2D graphics,
            BufferedImage portrait,
            int x,
            int y,
            int width,
            int height
    ) {
        double targetRatio = (double) width / height;
        double sourceRatio = (double) portrait.getWidth() / portrait.getHeight();
        int sourceX = 0;
        int sourceY = 0;
        int sourceWidth = portrait.getWidth();
        int sourceHeight = portrait.getHeight();
        if (sourceRatio > targetRatio) {
            sourceWidth = (int) Math.round(sourceHeight * targetRatio);
            sourceX = (portrait.getWidth() - sourceWidth) / 2;
        } else {
            sourceHeight = (int) Math.round(sourceWidth / targetRatio);
            sourceY = Math.max(0, (portrait.getHeight() - sourceHeight) / 3);
        }
        graphics.drawImage(
                portrait,
                x, y, x + width, y + height,
                sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight,
                null
        );
    }

    private void drawField(Graphics2D graphics, String label, String value, int x, int y) {
        graphics.setColor(new Color(48, 79, 112));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        graphics.drawString(label, x, y);
        graphics.setColor(new Color(15, 23, 42));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        graphics.drawString(value, x, y + 29);
    }

    private void drawCompactField(Graphics2D graphics, String label, String value, int x, int y) {
        graphics.setColor(new Color(48, 79, 112));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(label, x, y);
        graphics.setColor(new Color(15, 23, 42));
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, value.length() > 40 ? 16 : 21));
        graphics.drawString(value, x, y + 30);
    }

    private void drawPassportBackdrop(Graphics2D graphics, int width, int height) {
        var originalComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        graphics.setColor(new Color(57, 146, 154));
        graphics.fillOval(900, 120, 350, 350);
        graphics.fillOval(245, 430, 250, 250);
        graphics.setColor(new Color(230, 122, 99));
        graphics.fillOval(1030, 420, 420, 420);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
        graphics.setColor(new Color(20, 61, 104));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 118));
        graphics.drawString("D  E  M  O", 500, 390);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
        graphics.drawString("SYNTHETIC", 520, 600);
        graphics.setComposite(originalComposite);

        graphics.setColor(new Color(255, 255, 255, 125));
        graphics.fillRoundRect(28, 125, width - 56, height - 410, 24, 24);
    }

    private void drawDemoBadge(Graphics2D graphics) {
        graphics.setColor(new Color(20, 61, 104));
        graphics.fillRoundRect(1260, 28, 82, 62, 12, 12);
        graphics.setColor(new Color(240, 191, 87));
        graphics.fillOval(1274, 40, 34, 34);
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        graphics.drawString("D", 1285, 64);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        graphics.drawString("DEMO", 1310, 66);
    }

    private String fitMrz(String value) {
        String normalized = mrz(value);
        String padded = normalized + "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<";
        return padded.substring(0, 44);
    }

    private String passportDate(LocalDate value) {
        return value == null ? "NOT SET" : PASSPORT_DATE.format(value).toUpperCase(Locale.ENGLISH);
    }

    private String mrz(String value) {
        return value.toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z ]", "").replace(' ', '<');
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

    private byte[] pdf(DemoDocumentFixture fixture, LocalDate issueDate, LocalDate expiryDate) {
        List<String> lines = metadataLines(fixture, issueDate, expiryDate, false);
        StringBuilder stream = new StringBuilder();
        stream.append("q\n0.078 0.239 0.408 rg\n0 748 595 94 re f\nQ\n");
        stream.append("1 1 1 rg\n");
        appendPdfText(stream, "F2", 10, 42, 812,
                "FOWOCO SYNTHETIC DOCUMENT / DEMO ONLY");
        appendPdfText(stream, "F2", 21, 42, 778, documentHeading(fixture));
        stream.append("0.06 0.09 0.16 rg\n");
        stream.append("q\n0.94 0.96 0.98 rg\n34 112 527 612 re f\n")
                .append("0.58 0.68 0.78 RG\n34 112 527 612 re S\nQ\n");
        appendPdfText(stream, "F2", 12, 52, 695, fixture.title());
        int y = 660;
        for (String line : lines) {
            appendPdfText(stream, "F1", 10, 52, y, line);
            y -= 31;
        }
        appendPdfText(stream, "F2", 11, 52, 145,
                "DEMO SAMPLE ONLY / NOT VALID / NO GOVERNMENT SECURITY FEATURES");
        appendPdfText(stream, "F1", 8, 52, 126,
                "Generated deterministically from the linked worker and worker_document metadata.");
        byte[] streamBytes = stream.toString().getBytes(StandardCharsets.US_ASCII);

        List<byte[]> objects = List.of(
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Contents 4 0 R >>"),
                concat(ascii("<< /Length " + streamBytes.length + " >>\nstream\n"),
                        streamBytes, ascii("endstream")),
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")
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

    private byte[] hwp(DemoDocumentFixture fixture, LocalDate issueDate, LocalDate expiryDate) {
        String resource = "/demo-data/document-templates/employment-contract-template.hwp";
        try (InputStream template = Objects.requireNonNull(
                     SyntheticDocumentGenerator.class.getResourceAsStream(resource),
                     "missing synthetic HWP template: " + resource
             );
             POIFSFileSystem fileSystem = new POIFSFileSystem(template);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            String text = String.join("\n", metadataLines(fixture, issueDate, expiryDate, true));
            fileSystem.getRoot().createDocument(
                    "FOWOCO-Metadata",
                    new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))
            );
            fileSystem.writeFilesystem(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to generate synthetic HWP container", exception);
        }
    }

    private byte[] hwpx(DemoDocumentFixture fixture, LocalDate issueDate, LocalDate expiryDate) {
        String resource = switch (fixture.documentType()) {
            case CONTRACT -> "/demo-data/document-templates/employment-contract-template.hwpx";
            case EMPLOYMENT_EXTENSION_APPLICATION ->
                    "/demo-data/document-templates/employment-extension-template.hwpx";
            case INTEGRATED_APPLICATION ->
                    "/demo-data/document-templates/integrated-application-template.hwpx";
            default -> throw new IllegalArgumentException(
                    "no HWPX template is registered for " + fixture.documentType()
            );
        };
        try (InputStream template = Objects.requireNonNull(
                     SyntheticDocumentGenerator.class.getResourceAsStream(resource),
                     "missing synthetic HWPX template: " + resource
             );
             ZipInputStream input = new ZipInputStream(template, StandardCharsets.UTF_8);
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (ZipEntry source = input.getNextEntry(); source != null; source = input.getNextEntry()) {
                byte[] content = input.readAllBytes();
                if (source.getName().equals("Preview/PrvText.txt")) {
                    content = String.join("\n", metadataLines(fixture, issueDate, expiryDate, true))
                            .getBytes(StandardCharsets.UTF_8);
                } else if (source.getName().endsWith(".xml")
                        || source.getName().endsWith(".hpf")
                        || source.getName().endsWith(".rdf")) {
                    String text = new String(content, StandardCharsets.UTF_8);
                    content = applyHwpxTemplateValues(text, fixture, issueDate, expiryDate)
                            .getBytes(StandardCharsets.UTF_8);
                }
                addZipEntry(zip, source.getName(), content, source.getName().equals("mimetype"));
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to generate synthetic HWPX", exception);
        }
    }

    private List<String> metadataLines(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate,
            boolean includeDisplayName
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        List<String> lines = new ArrayList<>();
        lines.add("DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION");
        lines.add("DOCUMENT TITLE: " + fixture.title());
        lines.add("WORKER DOCUMENT ID: " + fixture.documentId());
        lines.add("WORKER ID: " + fixture.workerId());
        if (includeDisplayName) {
            lines.add("DB DISPLAY NAME: " + identity.displayName());
        }
        lines.add("HOLDER ENGLISH NAME: " + identity.englishName());
        lines.add("NATIONALITY: " + identity.nationality() + " (" + identity.nationalityCode() + ")");
        lines.add("PREFERRED LANGUAGE: " + identity.preferredLanguage());
        lines.add("VISA / STAY: " + identity.visaType());
        lines.add("DATE OF BIRTH: " + identity.birthDate() + " / SYNTHETIC");
        lines.add(documentNumberLabel(fixture).toUpperCase(Locale.ENGLISH) + ": " + documentNumber(fixture));
        lines.add("DOCUMENT TYPE: " + fixture.documentType().name());
        lines.add("SUBMISSION STATUS: " + fixture.status().name());
        lines.add("ISSUE DATE: " + metadataDate(issueDate));
        lines.add("EXPIRY DATE: " + metadataDate(expiryDate));
        lines.add("STORAGE FILE: " + fixture.storageFilename());
        lines.add("SYNTHETIC ADDRESS: " + identity.syntheticAddress());
        lines.add("NOT VALID / NO OFFICIAL LOGO, SIGNATURE, OR SECURITY PATTERN");
        return lines;
    }

    private String documentHeading(DemoDocumentFixture fixture) {
        return switch (fixture.documentType()) {
            case PASSPORT_COPY -> "PASSPORT COPY";
            case ARC -> fixture.storageFilename().contains("back")
                    ? "RESIDENCE CARD - BACK"
                    : "RESIDENCE CARD";
            case CONTRACT -> "STANDARD EMPLOYMENT CONTRACT";
            case PERMIT -> "EMPLOYMENT PERMIT";
            case EMPLOYMENT_EXTENSION_APPLICATION -> "EMPLOYMENT PERIOD EXTENSION APPLICATION";
            case INTEGRATED_APPLICATION -> "INTEGRATED APPLICATION";
            case RESIDENCE_PROOF -> "RESIDENCE PROOF";
        };
    }

    private String documentNumberLabel(DemoDocumentFixture fixture) {
        return fixture.documentType() == com.fowoco.server.worker.domain.DocumentType.ARC
                ? "Synthetic ARC No."
                : "Synthetic reference No.";
    }

    private String documentNumber(DemoDocumentFixture fixture) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        return switch (fixture.documentType()) {
            case PASSPORT_COPY -> identity.documentNumber();
            case ARC -> identity.alienRegistrationNumber();
            case CONTRACT -> "DEMO-CONTRACT-%02d-NOT-VALID".formatted(identity.portraitSeed());
            case PERMIT -> "DEMO-PERMIT-%02d-NOT-VALID".formatted(identity.portraitSeed());
            case EMPLOYMENT_EXTENSION_APPLICATION ->
                    "DEMO-EXT-%02d-DRAFT".formatted(identity.portraitSeed());
            case INTEGRATED_APPLICATION -> "DEMO-INT-%02d-DRAFT".formatted(identity.portraitSeed());
            case RESIDENCE_PROOF -> "DEMO-ADDR-%02d-NOT-VALID".formatted(identity.portraitSeed());
        };
    }

    private String metadataDate(LocalDate value) {
        return value == null ? "NOT SET" : value.toString();
    }

    private void appendPdfText(
            StringBuilder stream,
            String font,
            int size,
            int x,
            int y,
            String value
    ) {
        stream.append("BT\n/").append(font).append(' ').append(size)
                .append(" Tf\n1 0 0 1 ").append(x).append(' ').append(y)
                .append(" Tm\n(").append(pdfEscape(value)).append(") Tj\nET\n");
    }

    private void addZipEntry(
            ZipOutputStream zip,
            String name,
            byte[] content,
            boolean stored
    ) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        if (stored) {
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private String applyHwpxTemplateValues(
            String source,
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        String result = source
                .replace("FOWOCO_DEMO_COMPANY", "FOWOCO DEMO COMPANY")
                .replace("DEMO_HOLDER_NAME", xmlEscape(identity.englishName()))
                .replace("DEMO_NATIONALITY", xmlEscape(identity.nationality()))
                .replace("DEMO_PASSPORT_NO", xmlEscape(identity.documentNumber()))
                .replace("DEMO_SURNAME", xmlEscape(identity.surname()))
                .replace("DEMO_GIVEN_NAMES", xmlEscape(identity.givenNames()))
                .replace("DEMO-WORKER-06", "DEMO-WORKER-%02d".formatted(identity.portraitSeed()))
                .replace("DEMO_WORKPLACE_ADDRESS", xmlEscape(identity.syntheticAddress()))
                .replace("DEMO_HOME_COUNTRY_ADDRESS", xmlEscape(identity.syntheticAddress()))
                .replace("DEMO_HOME_ADDRESS", xmlEscape(identity.syntheticAddress()))
                .replace("9912315999999", "9999995%06d".formatted(identity.portraitSeed()))
                .replace("2098-01-01", metadataDate(issueDate))
                .replace("2099-01-01", metadataDate(expiryDate))
                .replace("2098. 01. 01.", dotDate(issueDate))
                .replace("2099. 01. 01.", dotDate(expiryDate))
                .replace("2097. 01. 01.", dotDate(identity.birthDate()))
                .replace(">2097<", ">" + identity.birthDate().getYear() + "<")
                .replace(">97<", ">%02d<".formatted(identity.birthDate().getMonthValue()))
                .replace(">98<", ">%02d<".formatted(identity.birthDate().getDayOfMonth()));
        if (result.contains("DEMO_HOLDER_NAME")
                || result.contains("DEMO_NATIONALITY")
                || result.contains("9912315999999")
                || result.contains("2098-01-01")
                || result.contains("2099-01-01")) {
            throw new IllegalStateException("synthetic HWPX template contains unresolved metadata tokens");
        }
        return result;
    }

    private String dotDate(LocalDate value) {
        return value == null
                ? "NOT SET"
                : "%04d. %02d. %02d.".formatted(
                        value.getYear(), value.getMonthValue(), value.getDayOfMonth()
                );
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
