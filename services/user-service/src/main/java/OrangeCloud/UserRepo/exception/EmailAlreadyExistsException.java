package OrangeCloud.UserRepo.exception;

public class EmailAlreadyExistsException extends CustomException {
    public EmailAlreadyExistsException() {
        super(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    public EmailAlreadyExistsException(String message) {
        super(ErrorCode.EMAIL_ALREADY_EXISTS, message);
    }
}
