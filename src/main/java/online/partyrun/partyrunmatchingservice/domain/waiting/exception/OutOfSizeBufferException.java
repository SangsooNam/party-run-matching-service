package online.partyrun.partyrunmatchingservice.domain.waiting.exception;

import online.partyrun.partyrunmatchingservice.global.exception.BadRequestException;

public class OutOfSizeBufferException extends BadRequestException {
    public OutOfSizeBufferException() {
        super("buffer size 보다 초과된 값을 요청하였습니다.");
    }
}
