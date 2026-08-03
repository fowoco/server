package com.fowoco.server.worker.infrastructure.seed;

import com.fowoco.server.worker.domain.WorkerStatus;
import java.util.List;
import java.util.UUID;

final class DemoWorkerSeedCatalog {

    private static final List<WorkerSeed> DEMO_WORKERS = List.of(
            activeWorker("92000000-0000-0000-0000-000000000001", "데모 근로자 01", "VN", "vi", 30, 180),
            activeWorker("92000000-0000-0000-0000-000000000002", "데모 근로자 02", "KH", "km", 60, 210),
            activeWorker("92000000-0000-0000-0000-000000000003", "데모 근로자 03", "NP", "ne", 90, 240),
            activeWorker("92000000-0000-0000-0000-000000000004", "데모 근로자 04", "ID", "id", 120, 270),
            activeWorker("92000000-0000-0000-0000-000000000005", "데모 근로자 05", "PH", "en", 180, 365),
            activeWorker("92000000-0000-0000-0000-000000000006", "응웬반A", "VN", "vi", 12, 180),
            activeWorker("92000000-0000-0000-0000-000000000007", "아디 수르야", "ID", "id", 365, 365),
            activeWorker("92000000-0000-0000-0000-000000000008", "소팔 타망", "NP", "ne", 21, 120),
            activeWorker("92000000-0000-0000-0000-000000000009", "라니 위자야", "ID", "id", 7, 90),
            activeWorker("92000000-0000-0000-0000-000000000010", "민 아웅", "MM", "my", 62, 20),
            activeWorker("92000000-0000-0000-0000-000000000011", "파티마 누르", "ID", "id", 35, 150),
            activeWorker("92000000-0000-0000-0000-000000000012", "트란 티 마이", "VN", "vi", 3, 45),
            activeWorker("92000000-0000-0000-0000-000000000013", "쩐 꾸옥 바오", "VN", "vi", 5, 75),
            onLeaveWorker("92000000-0000-0000-0000-000000000014", "분미 찬사몽", "LA", "lo", 7, 100),
            activeWorker("92000000-0000-0000-0000-000000000015", "찬다라 소쿤", "KH", "km", 21, 135),
            activeWorker("92000000-0000-0000-0000-000000000016", "니말 페레라", "LK", "si", 35, 180),
            activeWorker("92000000-0000-0000-0000-000000000017", "라훌 쿠마르", "IN", "hi", 62, 210),
            activeWorker("92000000-0000-0000-0000-000000000018", "모하메드 라힘", "BD", "bn", 75, 240),
            activeWorker("92000000-0000-0000-0000-000000000019", "누르 아지자", "ID", "id", 89, 270),
            activeWorker("92000000-0000-0000-0000-000000000020", "아웅 쪼 민", "MM", "my", 91, 300),
            onLeaveWorker("92000000-0000-0000-0000-000000000021", "수리야 프라사드", "LK", "ta", 150, 330),
            activeWorker("92000000-0000-0000-0000-000000000022", "프라딥 구룽", "NP", "ne", 240, 365),
            activeWorker("92000000-0000-0000-0000-000000000023", "솜차이 차이야", "TH", "th", 365, 400),
            activeWorker("92000000-0000-0000-0000-000000000024", "응우옌 티 란", "VN", "vi", 6, 60),
            onLeaveWorker("92000000-0000-0000-0000-000000000025", "데위 사푸트리", "ID", "id", null, 240),
            activeWorker("92000000-0000-0000-0000-000000000026", "띤 자르 윈", "MM", "my", 12, 90),
            activeWorker("92000000-0000-0000-0000-000000000027", "압둘 카림", "BD", "bn", 90, 180),
            activeWorker("92000000-0000-0000-0000-000000000028", "비벡 타파", "NP", "ne", 540, 540)
    );

    private static final List<WorkerSeed> TEST_WORKERS = List.of(
            activeWorker("93000000-0000-0000-0000-000000000001", "테스트 근로자 01", "TH", "th", 45, 190),
            activeWorker("93000000-0000-0000-0000-000000000002", "테스트 근로자 02", "MN", "mn", 75, 220),
            activeWorker("93000000-0000-0000-0000-000000000003", "테스트 근로자 03", "BD", "bn", 105, 250),
            activeWorker("93000000-0000-0000-0000-000000000004", "테스트 근로자 04", "UZ", "uz", 135, 280),
            activeWorker("93000000-0000-0000-0000-000000000005", "테스트 근로자 05", "LK", "si", 195, 370)
    );

    List<WorkerSeed> demoWorkers() {
        return DEMO_WORKERS;
    }

    List<WorkerSeed> testWorkers() {
        return TEST_WORKERS;
    }

    private static WorkerSeed activeWorker(
            String workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            Integer stayExpiryDays,
            int contractEndDays
    ) {
        return worker(
                workerId,
                displayName,
                nationalityCode,
                preferredLanguage,
                WorkerStatus.ACTIVE,
                stayExpiryDays,
                contractEndDays
        );
    }

    private static WorkerSeed onLeaveWorker(
            String workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            Integer stayExpiryDays,
            int contractEndDays
    ) {
        return worker(
                workerId,
                displayName,
                nationalityCode,
                preferredLanguage,
                WorkerStatus.ON_LEAVE,
                stayExpiryDays,
                contractEndDays
        );
    }

    private static WorkerSeed worker(
            String workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            Integer stayExpiryDays,
            int contractEndDays
    ) {
        return new WorkerSeed(
                UUID.fromString(workerId),
                displayName,
                nationalityCode,
                preferredLanguage,
                workStatus,
                stayExpiryDays,
                contractEndDays
        );
    }

    record WorkerSeed(
            UUID workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            Integer stayExpiryDays,
            int contractEndDays
    ) {
    }
}
