package com.fowoco.server.worker.infrastructure.seed;

import com.fowoco.server.worker.domain.WorkerStatus;
import java.util.List;
import java.util.UUID;

final class DemoWorkerSeedCatalog {

    private static final List<WorkerSeed> DEMO_WORKERS = List.of(
            activeWorker("92000000-0000-0000-0000-000000000001", "리웨이", "CN", "zh-Hans", 30, 180, 45, 35),
            activeWorker("92000000-0000-0000-0000-000000000002", "속 체아", "KH", "km", 60, 210, 75, 60),
            activeWorker("92000000-0000-0000-0000-000000000003", "아르준 타파", "NP", "en", 90, 240, 105, 95),
            activeWorker("92000000-0000-0000-0000-000000000004", "부디 산토소", "ID", "id", 120, 270, 135, 120),
            activeWorker("92000000-0000-0000-0000-000000000005", "마크 레예스", "PH", "en", 180, 365, 195, 185),
            activeWorker("92000000-0000-0000-0000-000000000006", "응웬반A", "VN", "vi", 45, 180, null, null),
            activeWorker("92000000-0000-0000-0000-000000000007", "아디 수르야", "ID", "id", 365, 365, 380, 370),
            activeWorker("92000000-0000-0000-0000-000000000008", "바트 에르덴", "MN", "mn", 21, 120, 36, 21),
            activeWorker("92000000-0000-0000-0000-000000000009", "라니 위자야", "ID", "id", 7, 90, 22, 12),
            activeWorker("92000000-0000-0000-0000-000000000010", "민 아웅", "MM", "en", 62, 20, 77, 62),
            activeWorker("92000000-0000-0000-0000-000000000011", "파티마 누르", "ID", "id", 35, 150, 50, 40),
            activeWorker("92000000-0000-0000-0000-000000000012", "트란 티 마이", "VN", "vi", 3, 45, 18, 3),
            activeWorker("92000000-0000-0000-0000-000000000013", "쩐 꾸옥 바오", "VN", "vi", 5, 75, 20, 10),
            onLeaveWorker("92000000-0000-0000-0000-000000000014", "아이다나 베크", "KG", "ky", 7, 100, 22, 7),
            activeWorker("92000000-0000-0000-0000-000000000015", "찬다라 소쿤", "KH", "km", 21, 135, 36, 26),
            activeWorker("92000000-0000-0000-0000-000000000016", "니말 페레라", "LK", "si", 35, 180, 50, 35),
            activeWorker("92000000-0000-0000-0000-000000000017", "알리 칸", "PK", "ur", 62, 210, 77, 67),
            activeWorker("92000000-0000-0000-0000-000000000018", "모하메드 라힘", "BD", "bn", 75, 240, 90, 75),
            activeWorker("92000000-0000-0000-0000-000000000019", "누르 아지자", "ID", "id", 89, 270, 104, 94),
            activeWorker("92000000-0000-0000-0000-000000000020", "아지즈 라히모프", "UZ", "uz", 91, 300, 106, 91),
            onLeaveWorker("92000000-0000-0000-0000-000000000021", "알렉세이 이바노프", "RU", "ru", 150, 330, 165, 155),
            activeWorker("92000000-0000-0000-0000-000000000022", "마리아 산토스", "PH", "fil", 240, 365, 255, 240),
            activeWorker("92000000-0000-0000-0000-000000000023", "솜차이 차이야", "TH", "th", 365, 400, 380, 370),
            activeWorker("92000000-0000-0000-0000-000000000024", "응우옌 티 란", "VN", "vi", 6, 60, 21, 6),
            onLeaveWorker("92000000-0000-0000-0000-000000000025", "데위 사푸트리", "ID", "id", null, 240, null, null),
            activeWorker("92000000-0000-0000-0000-000000000026", "조제 다 코스타", "TL", "tet", 12, 90, 27, 12),
            activeWorker("92000000-0000-0000-0000-000000000027", "압둘 카림", "BD", "bn", 90, 180, 105, 95),
            activeWorker("92000000-0000-0000-0000-000000000028", "비벡 타파", "NP", "en", 540, 540, 555, 540)
    );

    private static final List<WorkerSeed> TEST_WORKERS = List.of(
            activeWorker("93000000-0000-0000-0000-000000000001", "테스트 근로자 01", "TH", "th", 45, 190, 60, 50),
            activeWorker("93000000-0000-0000-0000-000000000002", "테스트 근로자 02", "MN", "mn", 75, 220, 90, 75),
            activeWorker("93000000-0000-0000-0000-000000000003", "테스트 근로자 03", "BD", "bn", 105, 250, 120, 110),
            activeWorker("93000000-0000-0000-0000-000000000004", "테스트 근로자 04", "UZ", "uz", 135, 280, 150, 135),
            activeWorker("93000000-0000-0000-0000-000000000005", "테스트 근로자 05", "LK", "si", 195, 370, 210, 200)
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
            int contractEndDays,
            Integer employmentPermitEndDays,
            Integer employmentActivityEndDays
    ) {
        return worker(
                workerId,
                displayName,
                nationalityCode,
                preferredLanguage,
                WorkerStatus.ACTIVE,
                stayExpiryDays,
                contractEndDays,
                employmentPermitEndDays,
                employmentActivityEndDays
        );
    }

    private static WorkerSeed onLeaveWorker(
            String workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            Integer stayExpiryDays,
            int contractEndDays,
            Integer employmentPermitEndDays,
            Integer employmentActivityEndDays
    ) {
        return worker(
                workerId,
                displayName,
                nationalityCode,
                preferredLanguage,
                WorkerStatus.ON_LEAVE,
                stayExpiryDays,
                contractEndDays,
                employmentPermitEndDays,
                employmentActivityEndDays
        );
    }

    private static WorkerSeed worker(
            String workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            Integer stayExpiryDays,
            int contractEndDays,
            Integer employmentPermitEndDays,
            Integer employmentActivityEndDays
    ) {
        return new WorkerSeed(
                UUID.fromString(workerId),
                displayName,
                nationalityCode,
                preferredLanguage,
                workStatus,
                stayExpiryDays,
                contractEndDays,
                employmentPermitEndDays,
                employmentActivityEndDays
        );
    }

    record WorkerSeed(
            UUID workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            Integer stayExpiryDays,
            int contractEndDays,
            Integer employmentPermitEndDays,
            Integer employmentActivityEndDays
    ) {
    }
}
