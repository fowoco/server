package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * DB 적재 없이 Golden Renewal 수동 E2E용 합성 신분서류만 로컬로 내보냅니다.
 */
public final class DemoGoldenUploadFixtureExporter {

    private static final Path DEFAULT_OUTPUT = Path.of("build", "demo-upload-fixtures");
    private static final List<String> GOLDEN_FILENAMES = List.of(
            "여권_인적사항면_응웬반A.png",
            "외국인등록증_앞면_응웬반A.png",
            "외국인등록증_뒷면_응웬반A.jpg"
    );

    private DemoGoldenUploadFixtureExporter() {
    }

    public static void main(String[] args) {
        Path output = args.length == 0 ? DEFAULT_OUTPUT : Path.of(args[0]);
        try {
            List<Path> exported = export(output, Clock.systemUTC());
            System.out.println("Golden Renewal 합성 제출 파일을 생성했습니다.");
            exported.forEach(path -> System.out.println(path.toAbsolutePath().normalize()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Golden Renewal 합성 제출 파일 생성에 실패했습니다.", exception);
        }
    }

    static List<Path> export(Path output, Clock clock) throws IOException {
        Files.createDirectories(output);
        LocalDate anchorDate = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        SyntheticDocumentGenerator generator = new SyntheticDocumentGenerator();

        List<DemoDocumentFixture> fixtures = DemoDocumentFixtureCatalog.fixtures().stream()
                .filter(fixture -> fixture.workerId().equals(DemoDocumentFixtureCatalog.GOLD_WORKER_ID))
                .filter(fixture -> GOLDEN_FILENAMES.contains(fixture.originalFilename()))
                .toList();
        if (fixtures.size() != GOLDEN_FILENAMES.size()) {
            throw new IllegalStateException("Golden Renewal 합성 신분서류 fixture 구성이 올바르지 않습니다.");
        }

        return fixtures.stream().map(fixture -> {
            Path destination = output.resolve(fixture.originalFilename());
            LocalDate issueDate = anchorDate.plusDays(fixture.issueDays());
            LocalDate expiryDate = anchorDate.plusDays(fixture.expiryDays());
            try {
                Files.write(destination, generator.generate(fixture, issueDate, expiryDate));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return destination;
        }).toList();
    }
}
