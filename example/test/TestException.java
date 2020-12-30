package test;

import lombok.NoArgsConstructor;
import lombok.StandardException;
import lombok.Getter;

@StandardException
//@NoArgsConstructor
public class TestException extends RuntimeException {
    @Getter
    private String toto;
//
//    public TestException(@Deprecated java.lang.Throwable bla) {
//        super(bla);
//    }
//
//    public TestException(String var1) {
//        super(var1);
//    }

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