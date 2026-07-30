package com.fowoco.server.file.domain;

/**
 * 악성파일 검사 상태.
 * 지금은 NOT_SCANNED로 고정하고 파일 연결은 허용한다. 실제 검증은 후속 이슈에서
 * 검사 인프라가 구축된 뒤 CLEAN/INFECTED 등의 실제 판정값을 채우게 될 것이다.
 */
public enum ScanStatus {
    NOT_SCANNED
}
