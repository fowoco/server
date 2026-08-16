package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.PassportIdentity;
import com.fowoco.server.worker.domain.DocumentType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

final class SyntheticDocumentGenerator {

    private static final String GOLD_WORKER_PORTRAIT = "/demo-data/nguyen-van-an-portrait.png";
    private static final String KOREAN_FONT = "/demo-data/fonts/NotoSansKR-Regular.otf";
    private static final Font DEMO_FONT = loadDemoFont();
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
        return switch (fixture.documentType()) {
            case PASSPORT_COPY -> passportBiographicalPage(fixture, issueDate, expiryDate, format);
            case ARC -> encodeImage(residenceCard(fixture, issueDate, expiryDate), format);
            default -> encodeImage(documentPages(fixture, issueDate, expiryDate).get(0), format);
        };
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

        } finally {
            graphics.dispose();
        }
        return encodeImage(image, format);
    }

    private BufferedImage residenceCard(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        return fixture.storageFilename().contains("back")
                ? residenceCardBack(fixture, issueDate, expiryDate)
                : residenceCardFront(fixture, issueDate, expiryDate);
    }

    private BufferedImage residenceCardFront(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        int width = 1200;
        int height = 760;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            graphics.setColor(new Color(9, 42, 91));
            graphics.fillRoundRect(8, 8, width - 16, height - 16, 58, 58);
            graphics.setPaint(new GradientPaint(
                    60, 45, new Color(239, 249, 255),
                    width - 70, height - 70, new Color(199, 225, 245)
            ));
            graphics.fillRoundRect(52, 48, width - 104, height - 96, 44, 44);

            graphics.setColor(new Color(19, 75, 130));
            graphics.setFont(demoFont(Font.BOLD, 39));
            graphics.drawString("FOWOCO", 92, 112);
            graphics.setFont(demoFont(Font.BOLD, 30));
            graphics.drawString("외국인등록증", 292, 105);
            graphics.setFont(demoFont(Font.BOLD, 17));
            graphics.drawString("RESIDENCE CARD · SYNTHETIC QA FIXTURE", 292, 131);

            graphics.setColor(new Color(62, 113, 158));
            graphics.setStroke(new BasicStroke(3f));
            graphics.drawLine(92, 151, 790, 151);

            drawResidenceField(graphics, "외국인등록번호 / CARD ID",
                    identity.alienRegistrationNumber(), 92, 188, 34);
            drawResidenceField(graphics, "성명 / NAME", identity.englishName(), 92, 274, 31);
            graphics.setFont(demoFont(Font.PLAIN, 19));
            graphics.setColor(new Color(52, 67, 84));
            graphics.drawString("DB 표시명: " + identity.displayName(), 92, 339);
            drawResidenceField(graphics, "국가·지역 / COUNTRY·REGION",
                    identity.nationality() + " (" + identity.nationalityCode() + ")", 92, 374, 25);
            drawResidenceField(graphics, "체류자격 / STATUS OF STAY", identity.visaType(), 445, 374, 25);
            drawResidenceField(graphics, "허가일자 / PERMISSION DATE",
                    metadataDate(issueDate), 92, 464, 23);
            drawResidenceField(graphics, "체류기간 만료일 / EXPIRY DATE",
                    metadataDate(expiryDate), 445, 464, 23);

            graphics.setColor(new Color(52, 87, 122));
            graphics.setFont(demoFont(Font.BOLD, 15));
            graphics.drawString("체류지 / ADDRESS", 92, 552);
            graphics.setColor(new Color(17, 34, 53));
            graphics.setFont(demoFont(Font.BOLD, 19));
            drawWrappedText(graphics, identity.syntheticAddress(), 92, 580, 680, 27);

            drawResidencePortrait(graphics, fixture, 836, 118, 260, 338);
            graphics.setColor(new Color(239, 248, 255));
            graphics.fillRoundRect(842, 488, 248, 100, 16, 16);
            graphics.setColor(new Color(176, 47, 43));
            graphics.setStroke(new BasicStroke(4f));
            graphics.drawRoundRect(842, 488, 248, 100, 16, 16);
            graphics.setFont(demoFont(Font.BOLD, 22));
            graphics.drawString("FOWOCO QA LAB", 870, 527);
            graphics.setFont(demoFont(Font.BOLD, 17));
            graphics.drawString("DEMO MARK · 관인 아님", 866, 560);

            graphics.setColor(new Color(35, 101, 161));
            graphics.fillRoundRect(52, 647, width - 104, 65, 0, 0);
            graphics.setColor(Color.WHITE);
            graphics.setFont(demoFont(Font.BOLD, 18));
            graphics.drawString(
                    "SYNTHETIC QA FIXTURE · NOT AN IDENTITY DOCUMENT · 실제 신분증이 아닙니다",
                    146,
                    686
            );
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage residenceCardBack(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        int width = 1200;
        int height = 760;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            graphics.setColor(new Color(9, 42, 91));
            graphics.fillRoundRect(8, 8, width - 16, height - 16, 58, 58);
            graphics.setPaint(new GradientPaint(
                    60, 45, new Color(239, 249, 255),
                    width - 70, height - 70, new Color(208, 230, 247)
            ));
            graphics.fillRoundRect(52, 48, width - 104, height - 96, 44, 44);

            graphics.setColor(new Color(19, 75, 130));
            graphics.setFont(demoFont(Font.BOLD, 32));
            graphics.drawString("FOWOCO 외국인등록증 뒷면", 84, 108);
            graphics.setFont(demoFont(Font.PLAIN, 17));
            graphics.drawString("RESIDENCE CARD · BACK · SYNTHETIC QA FIXTURE", 84, 137);
            graphics.setColor(new Color(62, 113, 158));
            graphics.setStroke(new BasicStroke(3f));
            graphics.drawLine(84, 158, 1110, 158);

            graphics.setColor(new Color(31, 76, 120));
            graphics.setFont(demoFont(Font.BOLD, 21));
            graphics.drawString("체류기간 / PERIOD OF STAY", 84, 207);
            drawTableRow(graphics, 84, 230, 1024, 58,
                    List.of("허가일자", "만료일자", "체류자격"),
                    List.of(metadataDate(issueDate), metadataDate(expiryDate), identity.visaType()));

            graphics.setFont(demoFont(Font.BOLD, 21));
            graphics.setColor(new Color(31, 76, 120));
            graphics.drawString("체류지 변경사항 / ADDRESS HISTORY", 84, 388);
            drawTableRow(graphics, 84, 410, 1024, 58,
                    List.of("신고일자", "체류지", "비고"),
                    List.of(
                            metadataDate(issueDate),
                            identity.syntheticAddress(),
                            "최초 등록 · DEMO"
                    ));

            graphics.setColor(new Color(52, 67, 84));
            graphics.setFont(demoFont(Font.PLAIN, 18));
            graphics.drawString("카드 ID: " + identity.alienRegistrationNumber(), 84, 575);
            graphics.drawString("문서 ID: " + fixture.documentId(), 84, 607);

            graphics.setColor(new Color(35, 101, 161));
            graphics.fillRoundRect(52, 647, width - 104, 65, 0, 0);
            graphics.setColor(Color.WHITE);
            graphics.setFont(demoFont(Font.BOLD, 18));
            graphics.drawString(
                    "SYNTHETIC QA FIXTURE · NOT AN IDENTITY DOCUMENT · 실제 신분증이 아닙니다",
                    146,
                    686
            );
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void drawResidenceField(
            Graphics2D graphics,
            String label,
            String value,
            int x,
            int y,
            int valueSize
    ) {
        graphics.setColor(new Color(52, 87, 122));
        graphics.setFont(demoFont(Font.BOLD, 15));
        graphics.drawString(label, x, y);
        graphics.setColor(new Color(17, 34, 53));
        graphics.setFont(demoFont(Font.BOLD, valueSize));
        graphics.drawString(value, x, y + 34);
    }

    private void drawResidencePortrait(
            Graphics2D graphics,
            DemoDocumentFixture fixture,
            int x,
            int y,
            int width,
            int height
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        String portraitResource = identity.portraitSeed() == 6
                ? GOLD_WORKER_PORTRAIT
                : "/demo-data/passport-portraits/worker-%02d.png".formatted(identity.portraitSeed());
        try (InputStream input = Objects.requireNonNull(
                SyntheticDocumentGenerator.class.getResourceAsStream(portraitResource),
                "missing synthetic residence-card portrait: " + portraitResource
        )) {
            BufferedImage portrait = ImageIO.read(input);
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(x - 10, y - 10, width + 20, height + 20, 18, 18);
            drawCroppedPortrait(graphics, portrait, x, y, width, height);
            graphics.setColor(new Color(66, 123, 174));
            graphics.setStroke(new BasicStroke(3f));
            graphics.drawRoundRect(x - 10, y - 10, width + 20, height + 20, 18, 18);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to load synthetic residence-card portrait", exception);
        }
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
        graphics.setColor(new Color(255, 255, 255, 238));
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
        List<BufferedImage> pages = documentPages(fixture, issueDate, expiryDate);
        List<byte[]> jpegPages = pages.stream().map(this::jpeg).toList();
        int objectCount = 2 + jpegPages.size() * 3;
        List<byte[]> objects = new ArrayList<>(objectCount);
        String kids = java.util.stream.IntStream.range(0, jpegPages.size())
                .mapToObj(index -> (3 + index * 3) + " 0 R")
                .collect(java.util.stream.Collectors.joining(" "));
        String searchableMetadata = metadataLines(fixture, issueDate, expiryDate, false).stream()
                .map(this::asciiOnly)
                .collect(java.util.stream.Collectors.joining(" | "));
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R /FowocoDemoMetadata ("
                + pdfEscape(searchableMetadata) + ") >>"));
        objects.add(ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + jpegPages.size() + " >>"));
        for (int index = 0; index < jpegPages.size(); index++) {
            int pageObject = 3 + index * 3;
            int contentsObject = pageObject + 1;
            int imageObject = pageObject + 2;
            byte[] jpeg = jpegPages.get(index);
            byte[] content = ascii("q\n595 0 0 842 0 0 cm\n/Im0 Do\nQ\n");
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /XObject << /Im0 " + imageObject + " 0 R >> >> "
                    + "/Contents " + contentsObject + " 0 R >>"));
            objects.add(concat(
                    ascii("<< /Length " + content.length + " >>\nstream\n"),
                    content,
                    ascii("endstream")
            ));
            objects.add(concat(
                    ascii("<< /Type /XObject /Subtype /Image /Width " + pages.get(index).getWidth()
                            + " /Height " + pages.get(index).getHeight()
                            + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode "
                            + "/Length " + jpeg.length + " >>\nstream\n"),
                    jpeg,
                    ascii("\nendstream")
            ));
        }

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

    private List<BufferedImage> documentPages(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        return switch (fixture.documentType()) {
            case PASSPORT_COPY -> List.of(passportCopyPage(fixture, issueDate, expiryDate));
            case ARC -> List.of(arcCombinedCopyPage(fixture, issueDate, expiryDate));
            case CONTRACT -> employmentContractPages(fixture, issueDate, expiryDate);
            case PERMIT -> List.of(employmentPermitPage(fixture, issueDate, expiryDate));
            case EMPLOYMENT_EXTENSION_APPLICATION ->
                    List.of(employmentExtensionPage(fixture, issueDate, expiryDate));
            case INTEGRATED_APPLICATION ->
                    List.of(integratedApplicationPage(fixture, issueDate, expiryDate));
            case IDENTITY_GUARANTY -> List.of(residenceProofPage(fixture, issueDate, expiryDate));
            case RESIDENCE_PROOF -> List.of(residenceProofPage(fixture, issueDate, expiryDate));
        };
    }

    private BufferedImage passportCopyPage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        BufferedImage page = a4Page();
        Graphics2D graphics = page.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "여권 사본", "PASSPORT COPY", "원본 대조용 합성 QA 문서");
            BufferedImage passport = ImageIO.read(new ByteArrayInputStream(
                    passportBiographicalPage(fixture, issueDate, expiryDate, "png")
            ));
            graphics.drawImage(passport, 82, 315, 1076, 754, null);
            drawMetadataFooter(graphics, fixture);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to render synthetic passport PDF", exception);
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private BufferedImage arcCombinedCopyPage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        BufferedImage page = a4Page();
        Graphics2D graphics = page.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "외국인등록증 통합 사본", "RESIDENCE CARD COMBINED COPY",
                    "앞면·뒷면 합성본 / 유효기간 임박 시나리오");
            BufferedImage front = residenceCardFront(fixture, issueDate, expiryDate);
            BufferedImage back = residenceCardBack(fixture, issueDate, expiryDate);
            graphics.drawImage(front, 90, 280, 1060, 670, null);
            graphics.drawImage(back, 90, 995, 1060, 670, null);
            drawMetadataFooter(graphics, fixture);
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private BufferedImage employmentPermitPage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        BufferedImage page = a4Page();
        Graphics2D graphics = page.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "외국인근로자 고용허가서", "EMPLOYMENT PERMIT",
                    "별지 제5호서식 필드 구조 기반 · 합성 QA 작성본");
            int y = 270;
            y = drawSection(graphics, "1. 고용허가 기본정보", y,
                    List.of(
                            field("고용허가서 발급번호", documentNumber(fixture)),
                            field("발급일자", metadataDate(issueDate)),
                            field("고용허가기간", metadataDate(issueDate) + " ~ " + metadataDate(expiryDate))
                    ));
            y = drawSection(graphics, "2. 사업장 정보", y,
                    List.of(
                            field("사업장명 / 대표자", demoCompanyName() + " / " + demoEmployerName()),
                            field("사업자등록번호 / 전화", demoBusinessNumber() + " / " + demoCompanyPhone()),
                            field("소재지", demoCompanyAddress()),
                            field("업종 / 사업내용", "제조업 / 합성 QA 데이터용 부품 조립"),
                            field("상시 근로자 수", "총 42명 (내국인 14명 / 외국인 28명)")
                    ));
            y = drawSection(graphics, "3. 외국인근로자 인적사항", y,
                    List.of(
                            field("성명(영어)", identity.englishName()),
                            field("국적", identity.nationality() + " / " + identity.nationalityCode()),
                            field("외국인등록번호", identity.alienRegistrationNumber()),
                            field("여권번호", identity.documentNumber()),
                            field("체류자격", identity.visaType()),
                            field("한국 내 주소", identity.syntheticAddress())
                    ));
            y = drawSection(graphics, "4. 고용 조건", y,
                    List.of(
                            field("근무장소 / 직무", demoCompanyAddress() + " / 제조 보조"),
                            field("근로시간", "08:00 ~ 17:00 (휴게 60분, 주 40시간)"),
                            field("월 통상임금", "2,350,000원 (합성값)"),
                            field("숙식", "기숙사 제공 / 중식 제공 / 근로자 부담 0원"),
                            field("가입보험", "고용 · 산재 · 건강 · 출국만기 · 보증")
                    ));
            drawSignatureBlock(graphics, y + 18, issueDate, demoEmployerName(), identity.englishName(), true);
            drawMetadataFooter(graphics, fixture);
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private List<BufferedImage> employmentContractPages(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        BufferedImage first = a4Page();
        Graphics2D graphics = first.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "표준근로계약서", "STANDARD LABOR CONTRACT",
                    "외국인근로자 고용법 시행규칙 별지 제6호서식 구조 기반");
            int y = 270;
            y = drawSection(graphics, "사용자 / Employer", y,
                    List.of(
                            field("업체명", demoCompanyName()),
                            field("전화번호", demoCompanyPhone()),
                            field("소재지", demoCompanyAddress()),
                            field("사용자 성명", demoEmployerName()),
                            field("사업자등록번호", demoBusinessNumber())
                    ));
            y = drawSection(graphics, "근로자 / Employee", y,
                    List.of(
                            field("성명", identity.englishName() + " (" + identity.displayName() + ")"),
                            field("생년월일", identity.birthDate().toString()),
                            field("본국 주소", homeCountryAddress(identity)),
                            field("국적 / 체류자격", identity.nationality() + " / " + identity.visaType())
                    ));
            y = drawSection(graphics, "1-3. 계약기간 · 근로장소 · 업무", y,
                    List.of(
                            field("근로계약기간", metadataDate(issueDate) + " ~ " + metadataDate(expiryDate)),
                            field("수습기간", "미활용"),
                            field("근로장소", demoCompanyAddress()),
                            field("업종 / 직무", "제조업 / 합성 QA 데이터용 부품 조립 및 검수")
                    ));
            y = drawSection(graphics, "4-6. 근로시간 · 휴게 · 휴일", y,
                    List.of(
                            field("근로시간", "08:00 ~ 17:00 / 주 40시간"),
                            field("평균 시간외 근로", "1일 1시간 이내 (사전 합의 시)"),
                            field("교대제", "미운영"),
                            field("휴게시간", "12:00 ~ 13:00 / 1일 60분"),
                            field("휴일", "매주 일요일 및 법정 공휴일 / 유급")
                    ));
            drawMetadataFooter(graphics, fixture);
        } finally {
            graphics.dispose();
        }

        BufferedImage second = a4Page();
        graphics = second.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "표준근로계약서 (뒤쪽)", "STANDARD LABOR CONTRACT · PAGE 2",
                    "임금·지급방법·숙식·서명란 작성본");
            int y = 270;
            y = drawSection(graphics, "7. 임금 / Payment", y,
                    List.of(
                            field("월 통상임금", "2,350,000원 (합성값)"),
                            field("기본급", "2,250,000원 / 월급"),
                            field("고정 수당", "직무수당 100,000원"),
                            field("상여금", "해당 없음"),
                            field("연장·야간·휴일근로", "통상임금의 50% 가산 지급")
                    ));
            y = drawSection(graphics, "8-9. 임금 지급", y,
                    List.of(
                            field("지급일", "매월 25일 (휴일이면 전 영업일)"),
                            field("지급방법", "근로자 명의 계좌 입금")
                    ));
            y = drawSection(graphics, "10. 숙식 제공", y,
                    List.of(
                            field("숙박시설", "제공 / 사업장 외부 기숙사 / 근로자 부담 0원"),
                            field("식사", "중식 제공 / 근로자 부담 0원")
                    ));
            y = drawSection(graphics, "11-12. 준수 및 기타", y,
                    List.of(
                            field("준수", "근로계약·취업규칙·단체협약을 성실히 이행"),
                            field("기타", "미정 사항은 근로기준법에 따름")
                    ));
            drawSignatureBlock(graphics, y + 35, issueDate, demoEmployerName(), identity.englishName(), true);
            drawMetadataFooter(graphics, fixture);
        } finally {
            graphics.dispose();
        }
        return List.of(first, second);
    }

    private BufferedImage employmentExtensionPage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        BufferedImage page = a4Page();
        Graphics2D graphics = page.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "취업기간 만료자 취업활동 기간 연장신청서",
                    "EMPLOYMENT ACTIVITY PERIOD EXTENSION APPLICATION",
                    "별지 제12호의3서식(2024. 1. 10. 개정) 기반 · 작성 완료 미서명 초안");
            int y = 270;
            y = drawSection(graphics, "사업장", y, List.of(
                    field("사업장명 / 대표자", demoCompanyName() + " / " + demoEmployerName()),
                    field("소재지", demoCompanyAddress()),
                    field("전화 / 사업자등록번호", demoCompanyPhone() + " / " + demoBusinessNumber()),
                    field("사업의 종류", "제조업 / 합성 QA 데이터용 부품 조립")
            ));
            y = drawSection(graphics, "사실관계 확인", y, List.of(
                    field("도입 업종", "☑ 해당"),
                    field("내국인 고용조정 이직 없음", "☑ 확인"),
                    field("임금체불 없음", "☑ 확인"),
                    field("고용·산재보험", "☑ 가입"),
                    field("출국만기·보증보험", "☑ 가입")
            ));
            y = drawSection(graphics, "취업기간 만료 외국인근로자", y, List.of(
                    field("성명", identity.englishName()),
                    field("외국인등록번호", identity.alienRegistrationNumber()),
                    field("국적 / 여권번호", identity.nationality() + " / " + identity.documentNumber()),
                    field("체류기간 만료일", metadataDate(expiryDate)),
                    field("서명 또는 날인", "미서명 · DRAFT")
            ));
            y = drawSection(graphics, "첨부서류", y, List.of(
                    field("1", "외국인등록증 사본"),
                    field("2", "여권 사본"),
                    field("3", "표준근로계약서 사본")
            ));
            drawSignatureBlock(graphics, y + 20, issueDate, demoEmployerName(), identity.englishName(), false);
            drawMetadataFooter(graphics, fixture);
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private BufferedImage integratedApplicationPage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        BufferedImage page = a4Page();
        Graphics2D graphics = page.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "통합신청서(신고서)", "APPLICATION FORM (REPORT FORM)",
                    "별지 제34호서식 · 체류기간 연장허가 작성 완료 미서명 초안");
            int y = 270;
            y = drawSection(graphics, "신청/신고 선택", y, List.of(
                    field("체류기간 연장허가", "☑ 선택"),
                    field("그 밖의 신청", "☐ 미선택")
            ));
            y = drawSection(graphics, "신청인", y, List.of(
                    field("성명", identity.surname() + " / " + identity.givenNames()),
                    field("생년월일 / 성별", identity.birthDate() + " / " + identity.sex()),
                    field("국적", identity.nationality()),
                    field("외국인등록번호", identity.alienRegistrationNumber()),
                    field("여권번호", identity.documentNumber()),
                    field("여권 발급일 / 유효기간", metadataDate(issueDate) + " / " + metadataDate(expiryDate))
            ));
            y = drawSection(graphics, "주소·연락처", y, List.of(
                    field("대한민국 내 주소", identity.syntheticAddress()),
                    field("휴대전화", syntheticWorkerPhone(identity)),
                    field("본국 주소", homeCountryAddress(identity)),
                    field("전자우편", syntheticWorkerEmail(identity))
            ));
            y = drawSection(graphics, "근무처·소득", y, List.of(
                    field("원 근무처", demoCompanyName()),
                    field("사업자등록번호 / 전화", demoBusinessNumber() + " / " + demoCompanyPhone()),
                    field("직업", "제조 보조"),
                    field("연 소득금액", "2,820만원 (합성값)")
            ));
            drawSignatureBlock(graphics, y + 18, issueDate, "신청인 미서명", identity.englishName(), false);
            drawMetadataFooter(graphics, fixture);
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private BufferedImage residenceProofPage(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        BufferedImage page = a4Page();
        Graphics2D graphics = page.createGraphics();
        try {
            enableHighQualityRendering(graphics);
            drawFormHeader(graphics, "체류지 제공 확인서", "PROOF OF RESIDENCE",
                    "합성 주소·합성 제공자 정보로 작성된 QA 증빙");
            int y = 300;
            y = drawSection(graphics, "체류자", y, List.of(
                    field("성명", identity.englishName() + " (" + identity.displayName() + ")"),
                    field("국적 / 체류자격", identity.nationality() + " / " + identity.visaType()),
                    field("외국인등록번호", identity.alienRegistrationNumber()),
                    field("여권번호", identity.documentNumber())
            ));
            y = drawSection(graphics, "체류지", y, List.of(
                    field("주소", identity.syntheticAddress()),
                    field("제공 형태", "회사 기숙사 무상 제공"),
                    field("제공 기간", metadataDate(issueDate) + " ~ " + metadataDate(expiryDate))
            ));
            y = drawSection(graphics, "제공자", y, List.of(
                    field("회사 / 대표자", demoCompanyName() + " / " + demoEmployerName()),
                    field("사업자등록번호", demoBusinessNumber()),
                    field("연락처", demoCompanyPhone()),
                    field("확인 문구", "위 합성 근로자에게 상기 체류지를 제공함을 확인합니다.")
            ));
            drawSignatureBlock(graphics, y + 35, issueDate, demoEmployerName(), identity.englishName(), true);
            drawMetadataFooter(graphics, fixture);
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private BufferedImage a4Page() {
        BufferedImage page = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = page.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, page.getWidth(), page.getHeight());
        } finally {
            graphics.dispose();
        }
        return page;
    }

    private void drawFormHeader(
            Graphics2D graphics,
            String koreanTitle,
            String englishTitle,
            String subtitle
    ) {
        graphics.setColor(new Color(14, 54, 96));
        graphics.fillRect(0, 0, 1240, 78);
        graphics.setColor(Color.WHITE);
        graphics.setFont(demoFont(Font.BOLD, 21));
        graphics.drawString("FOWOCO SYNTHETIC DOCUMENT · DEMO ONLY", 70, 49);
        graphics.setColor(new Color(17, 34, 53));
        graphics.setFont(demoFont(Font.BOLD, koreanTitle.length() > 23 ? 37 : 44));
        graphics.drawString(koreanTitle, 70, 153);
        graphics.setColor(new Color(47, 87, 126));
        graphics.setFont(demoFont(Font.BOLD, 24));
        graphics.drawString(englishTitle, 70, 194);
        graphics.setColor(new Color(71, 85, 105));
        graphics.setFont(demoFont(Font.PLAIN, 18));
        graphics.drawString(subtitle, 70, 229);
        graphics.setColor(new Color(87, 129, 168));
        graphics.setStroke(new BasicStroke(3f));
        graphics.drawLine(70, 246, 1170, 246);
    }

    private int drawSection(
            Graphics2D graphics,
            String title,
            int y,
            List<FormField> fields
    ) {
        graphics.setColor(new Color(220, 233, 244));
        graphics.fillRoundRect(70, y, 1100, 42, 8, 8);
        graphics.setColor(new Color(20, 61, 104));
        graphics.setFont(demoFont(Font.BOLD, 20));
        graphics.drawString(title, 88, y + 29);
        int rowY = y + 54;
        for (FormField field : fields) {
            graphics.setColor(new Color(219, 226, 233));
            graphics.drawLine(70, rowY + 42, 1170, rowY + 42);
            graphics.setColor(new Color(52, 87, 122));
            graphics.setFont(demoFont(Font.BOLD, 16));
            graphics.drawString(field.label(), 88, rowY + 25);
            graphics.setColor(new Color(17, 34, 53));
            graphics.setFont(demoFont(Font.PLAIN, field.value().length() > 62 ? 15 : 18));
            drawWrappedText(graphics, field.value(), 380, rowY + 25, 770, 22);
            rowY += 48;
        }
        return rowY + 16;
    }

    private void drawSignatureBlock(
            Graphics2D graphics,
            int requestedY,
            LocalDate date,
            String employer,
            String employee,
            boolean completed
    ) {
        int y = Math.min(requestedY, 1510);
        graphics.setColor(new Color(241, 245, 249));
        graphics.fillRoundRect(70, y, 1100, 125, 12, 12);
        graphics.setColor(new Color(51, 65, 85));
        graphics.setFont(demoFont(Font.BOLD, 18));
        graphics.drawString("작성일 / Date: " + metadataDate(date), 92, y + 34);
        graphics.drawString("사용자 / Employer: " + employer
                + (completed ? "  [합성 서명 완료]" : "  [미서명 초안]"), 92, y + 70);
        graphics.drawString("근로자 / Employee: " + employee
                + (completed ? "  [합성 서명 완료]" : "  [미서명 초안]"), 92, y + 106);
    }

    private void drawMetadataFooter(Graphics2D graphics, DemoDocumentFixture fixture) {
        graphics.setColor(new Color(71, 85, 105));
        graphics.setFont(demoFont(Font.PLAIN, 13));
        graphics.drawString("worker_document_id: " + fixture.documentId(), 70, 1648);
        graphics.drawString("worker_id: " + fixture.workerId(), 70, 1672);
        graphics.setColor(new Color(176, 47, 43));
        graphics.fillRect(0, 1692, 1240, 62);
        graphics.setColor(Color.WHITE);
        graphics.setFont(demoFont(Font.BOLD, 18));
        graphics.drawString(
                "FOWOCO DEMO · NOT VALID · 실제 제출 불가 · NO OFFICIAL LOGO, SEAL OR SECURITY FEATURE",
                119,
                1731
        );
    }

    private void drawWrappedText(
            Graphics2D graphics,
            String value,
            int x,
            int y,
            int maxWidth,
            int lineHeight
    ) {
        StringBuilder line = new StringBuilder();
        int currentY = y;
        for (String word : value.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && graphics.getFontMetrics().stringWidth(candidate) > maxWidth) {
                graphics.drawString(line.toString(), x, currentY);
                currentY += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            graphics.drawString(line.toString(), x, currentY);
        }
    }

    private void drawTableRow(
            Graphics2D graphics,
            int x,
            int y,
            int width,
            int rowHeight,
            List<String> labels,
            List<String> values
    ) {
        int cellWidth = width / labels.size();
        graphics.setStroke(new BasicStroke(2f));
        for (int index = 0; index < labels.size(); index++) {
            int cellX = x + cellWidth * index;
            graphics.setColor(new Color(218, 232, 244));
            graphics.fillRect(cellX, y, cellWidth, rowHeight);
            graphics.setColor(new Color(64, 103, 140));
            graphics.drawRect(cellX, y, cellWidth, rowHeight * 2);
            graphics.drawLine(cellX, y + rowHeight, cellX + cellWidth, y + rowHeight);
            graphics.setFont(demoFont(Font.BOLD, 17));
            graphics.drawString(labels.get(index), cellX + 14, y + 36);
            graphics.setColor(new Color(17, 34, 53));
            graphics.setFont(demoFont(Font.PLAIN, values.get(index).length() > 30 ? 13 : 17));
            drawWrappedText(graphics, values.get(index), cellX + 14, y + rowHeight + 35,
                    cellWidth - 28, 19);
        }
    }

    private byte[] jpeg(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "jpg", output)) {
                throw new IllegalStateException("JPEG writer is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to encode synthetic PDF page", exception);
        }
    }

    private String asciiOnly(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private FormField field(String label, String value) {
        return new FormField(label, value);
    }

    private String demoCompanyName() {
        return "FOWOCO DEMO COMPANY";
    }

    private String demoEmployerName() {
        return "FOWOCO DEMO HR";
    }

    private String demoBusinessNumber() {
        return "DEMO-BIZ-00006";
    }

    private String demoCompanyPhone() {
        return "070-DEMO-2606";
    }

    private String demoCompanyAddress() {
        return "FOWOCO DEMO INDUSTRIAL PARK 6, SAMPLE-RO, DEMO CITY";
    }

    private String homeCountryAddress(PassportIdentity identity) {
        return "DEMO HOME " + identity.portraitSeed() + ", "
                + identity.nationality() + " · SYNTHETIC";
    }

    private String syntheticWorkerPhone(PassportIdentity identity) {
        return "010-DEMO-%04d".formatted(identity.portraitSeed());
    }

    private String syntheticWorkerEmail(PassportIdentity identity) {
        return "worker-%02d@example.invalid".formatted(identity.portraitSeed());
    }

    private static Font loadDemoFont() {
        try (InputStream input = Objects.requireNonNull(
                SyntheticDocumentGenerator.class.getResourceAsStream(KOREAN_FONT),
                "missing bundled demo font: " + KOREAN_FONT
        )) {
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to load bundled demo font", exception);
        } catch (FontFormatException exception) {
            throw new IllegalStateException("invalid bundled demo font", exception);
        }
    }

    private Font demoFont(int style, float size) {
        return DEMO_FONT.deriveFont(style, size);
    }

    private record FormField(String label, String value) {
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
        lines.addAll(formSpecificMetadata(fixture, issueDate, expiryDate));
        lines.add("NOT VALID / NO OFFICIAL LOGO, SIGNATURE, OR SECURITY PATTERN");
        return lines;
    }

    private List<String> formSpecificMetadata(
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        PassportIdentity identity = Objects.requireNonNull(fixture.passportIdentity());
        return switch (fixture.documentType()) {
            case PASSPORT_COPY -> List.of(
                    "PASSPORT HOLDER: " + identity.englishName(),
                    "PASSPORT NO: " + identity.documentNumber()
            );
            case ARC -> List.of(
                    "ARC HOLDER: " + identity.englishName(),
                    "ARC NO: " + identity.alienRegistrationNumber(),
                    "PERMISSION PERIOD: " + metadataDate(issueDate) + " TO " + metadataDate(expiryDate)
            );
            case CONTRACT -> List.of(
                    "EMPLOYER: " + demoCompanyName() + " / " + demoEmployerName(),
                    "BUSINESS NO: " + demoBusinessNumber(),
                    "WORKPLACE: " + demoCompanyAddress(),
                    "CONTRACT PERIOD: " + metadataDate(issueDate) + " TO " + metadataDate(expiryDate),
                    "WORKING HOURS: 08:00 TO 17:00 / BREAK 60 MIN / 40 HOURS PER WEEK",
                    "MONTHLY WAGE: KRW 2,350,000 / PAYMENT DAY: 25",
                    "ACCOMMODATION: PROVIDED / MEAL: LUNCH PROVIDED / EMPLOYEE COST: KRW 0",
                    "SIGNATURE STATE: SYNTHETIC SIGNATURE COMPLETED"
            );
            case PERMIT -> List.of(
                    "EMPLOYER: " + demoCompanyName() + " / " + demoBusinessNumber(),
                    "WORKPLACE: " + demoCompanyAddress(),
                    "EMPLOYEE: " + identity.englishName(),
                    "WORK: MANUFACTURING ASSISTANT / 08:00 TO 17:00",
                    "MONTHLY WAGE: KRW 2,350,000",
                    "INSURANCE: EMPLOYMENT / INDUSTRIAL ACCIDENT / HEALTH / DEPARTURE / GUARANTEE"
            );
            case EMPLOYMENT_EXTENSION_APPLICATION -> List.of(
                    "FORM: ANNEX 12-3 / REVISION 2024-01-10",
                    "EMPLOYER: " + demoCompanyName() + " / " + demoBusinessNumber(),
                    "WORKER: " + identity.englishName() + " / " + identity.alienRegistrationNumber(),
                    "FACT CHECKS: ELIGIBLE INDUSTRY=YES; NO LAYOFF=YES; NO WAGE ARREARS=YES; "
                            + "INSURANCE=YES; DEPARTURE/GUARANTEE=YES",
                    "ATTACHMENTS: ARC COPY / PASSPORT COPY / STANDARD LABOR CONTRACT COPY",
                    "SIGNATURE STATE: FILLED DRAFT / UNSIGNED"
            );
            case INTEGRATED_APPLICATION -> List.of(
                    "FORM: IMMIGRATION ANNEX 34 / EXTENSION OF SOJOURN PERIOD=SELECTED",
                    "APPLICANT: " + identity.englishName() + " / " + identity.alienRegistrationNumber(),
                    "PASSPORT: " + identity.documentNumber(),
                    "KOREA ADDRESS: " + identity.syntheticAddress(),
                    "HOME COUNTRY ADDRESS: " + homeCountryAddress(identity),
                    "PHONE / EMAIL: " + syntheticWorkerPhone(identity) + " / " + syntheticWorkerEmail(identity),
                    "WORKPLACE: " + demoCompanyName() + " / " + demoBusinessNumber(),
                    "ANNUAL INCOME: KRW 28,200,000 / OCCUPATION: MANUFACTURING ASSISTANT",
                    "SIGNATURE STATE: FILLED DRAFT / UNSIGNED"
            );
            case IDENTITY_GUARANTY -> List.of(
                    "GUARANTEED PERSON: " + identity.englishName(),
                    "GUARANTOR: " + demoEmployerName() + " / " + demoCompanyName(),
                    "SIGNATURE STATE: FILLED DRAFT / UNSIGNED"
            );
            case RESIDENCE_PROOF -> List.of(
                    "RESIDENT: " + identity.englishName() + " / " + identity.alienRegistrationNumber(),
                    "PROVIDED ADDRESS: " + identity.syntheticAddress(),
                    "PROVIDER: " + demoCompanyName() + " / " + demoEmployerName(),
                    "PROVISION: COMPANY DORMITORY / EMPLOYEE COST KRW 0"
            );
        };
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
            case IDENTITY_GUARANTY -> "IDENTITY GUARANTY";
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
            case IDENTITY_GUARANTY -> "DEMO-GUARANTY-%02d-DRAFT".formatted(identity.portraitSeed());
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
                .replace("FOWOCO_DEMO_COMPANY", xmlEscape(demoCompanyName()))
                .replace("DEMO_HOLDER_NAME", xmlEscape(identity.englishName()))
                .replace("DEMO_NATIONALITY", xmlEscape(identity.nationality()))
                .replace("DEMO_PASSPORT_NO", xmlEscape(identity.documentNumber()))
                .replace("DEMO_SURNAME", xmlEscape(identity.surname()))
                .replace("DEMO_GIVEN_NAMES", xmlEscape(identity.givenNames()))
                .replace("DEMO-WORKER-06", "DEMO-WORKER-%02d".formatted(identity.portraitSeed()))
                .replace("DEMO_WORKPLACE_ADDRESS", xmlEscape(demoCompanyAddress()))
                .replace("DEMO_HOME_COUNTRY_ADDRESS", xmlEscape(homeCountryAddress(identity)))
                .replace("DEMO_HOME_ADDRESS", xmlEscape(
                        fixture.documentType() == DocumentType.CONTRACT
                                ? homeCountryAddress(identity)
                                : identity.syntheticAddress()
                ))
                .replace("000-0000-0000", xmlEscape(demoCompanyPhone()))
                .replace("000-00-00000", xmlEscape(demoBusinessNumber()))
                .replace("2098-01-01", metadataDate(issueDate))
                .replace("2099-01-01", metadataDate(expiryDate))
                .replace("2098. 01. 01.", dotDate(issueDate))
                .replace("2099. 01. 01.", dotDate(expiryDate))
                .replace("2097. 01. 01.", dotDate(identity.birthDate()))
                .replace(">2097<", ">" + identity.birthDate().getYear() + "<")
                .replace(">97<", ">%02d<".formatted(identity.birthDate().getMonthValue()))
                .replace(">98<", ">%02d<".formatted(identity.birthDate().getDayOfMonth()));
        result = switch (fixture.documentType()) {
            case CONTRACT -> result
                    .replace("    시    분  ~   시    분", "08시 00분 ~ 17시 00분")
                    .replace("1일 평균 시간외 근로시간:    시간", "1일 평균 시간외 근로시간: 1시간")
                    .replace(" 변동 가능:       시간 이내)", " 변동 가능: 1시간 이내)")
                    .replace("1일      분", "1일 60분")
                    .replace("[ ]일요일 [ ]공휴일", "[v]일요일 [v]공휴일")
                    .replace("1) 월 통상임금       (          )원", "1) 월 통상임금 (2,350,000)원")
                    .replace(" - 기본급[(월, 시간, 일, 주)급]   (           )원  ",
                            " - 기본급[(월)급] (2,250,000)원")
                    .replace(" - 고정적 수당: (        수당 :         원), (        수당:          원)",
                            " - 고정적 수당: (직무 수당: 100,000원), (기타 수당: 0원)")
                    .replace(" - 상여금 (          원)", " - 상여금 (0원)")
                    .replace("매월 (     )일 또는 매주", "매월 (25)일 또는 매주")
                    .replace("[  ]직접 지급,   [  ]통장 입금", "[  ]직접 지급,   [v]통장 입금")
                    .replace(" - 숙박시설 제공 여부: [  ]제공   [  ]미제공",
                            " - 숙박시설 제공 여부: [v]제공   [  ]미제공")
                    .replace(" - 숙박시설 제공 시 근로자 부담금액: 매월        원     ",
                            " - 숙박시설 제공 시 근로자 부담금액: 매월 0원")
                    .replace("- 식사 제공 여부: 제공([  ]조식, [  ]중식, [  ]석식)    [  ]미제공   ",
                            "- 식사 제공 여부: 제공([  ]조식, [v]중식, [  ]석식) [  ]미제공")
                    .replace(" - 식사 제공 시 근로자 부담금액: 매월        원",
                            " - 식사 제공 시 근로자 부담금액: 매월 0원");
            case EMPLOYMENT_EXTENSION_APPLICATION -> result
                    .replace("2014.7.28", "2024. 1. 10.")
                    .replace("ㆍ [  ]", "ㆍ [v]")
                    .replace("DEMO AUTHORITY - NOT FOR SUBMISSION", "미서명 초안 / DEMO NOT VALID");
            case INTEGRATED_APPLICATION -> replaceRegistrationNumberCells(
                    result
                            .replace("demo.worker@example.invalid", syntheticWorkerEmail(identity))
                            .replace("APPLICATION FORM (REPORT FORM)",
                                    "APPLICATION FORM (REPORT FORM) · FILLED DRAFT / UNSIGNED"),
                    identity
            );
            default -> result;
        };
        if (result.contains("DEMO_HOLDER_NAME")
                || result.contains("DEMO_NATIONALITY")
                || result.contains("2098-01-01")
                || result.contains("2099-01-01")) {
            throw new IllegalStateException("synthetic HWPX template contains unresolved metadata tokens");
        }
        return result;
    }

    private String replaceRegistrationNumberCells(String source, PassportIdentity identity) {
        int marker = source.indexOf("Foreign Resident Registration No");
        if (marker < 0) {
            return source;
        }
        String prefix = source.substring(0, marker);
        String tail = source.substring(marker);
        String replacement = "DEMO-ARC-%04d".formatted(identity.portraitSeed());
        Pattern digitCell = Pattern.compile("<hp:t>([0-9])</hp:t>");
        Matcher matcher = digitCell.matcher(tail);
        StringBuffer output = new StringBuffer();
        int index = 0;
        while (matcher.find() && index < replacement.length()) {
            String updatedCell = "<hp:t>" + replacement.charAt(index++) + "</hp:t>";
            matcher.appendReplacement(output, Matcher.quoteReplacement(updatedCell));
        }
        matcher.appendTail(output);
        if (index != replacement.length()) {
            throw new IllegalStateException("integrated application registration number cells are incomplete");
        }
        return prefix + output;
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
