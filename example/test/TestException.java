package test;

import lombok.StandardException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@StandardException
//@AllArgsConstructor
public class TestException extends RuntimeException {
    @Getter
    private String toto;

    public static void main(String[] args) {
        System.out.println("TOTO WAS HERE: " + new TestException().getToto());
    }
}