package test;

import lombok.StandardException;
import lombok.Getter;

@StandardException
//@AllArgsConstructor
public class TestException extends RuntimeException {
    @Getter
    private String toto;

    public TestException(@Deprecated java.lang.Throwable bla) {
        super(bla);
    }

    public static void main(String[] args) {
        try {
//            System.out.println("TOTO WAS HERE: " + new IllegalArgumentException("toto").getCause().getMessage());
            throw new IllegalArgumentException("toto");
        } catch (Exception e) {
            throw new TestException("meh", e);
//            throw new TestException();
        }
    }
}