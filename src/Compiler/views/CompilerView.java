package Compiler.views;

import Compiler.models.Lexer.Lexer;
import Compiler.models.Logger.Loggable;
import Compiler.models.Parser.Ast.Interfaces.Expr;
import Compiler.models.Parser.Parser;

import java.io.FileWriter;
import java.io.IOException;

public class CompilerView extends Loggable {
    /* == Public interface == */
    public void viewCompilation(String expr) {
        Lexer lexer = new Lexer(expr);
        System.out.println("=== Lexer output ===");
        System.out.println("\u001B[34m" + lexer.toPretty() + "\u001B[0m");

        Expr ast = new Parser(lexer).parse();
        System.out.println("=== Parser output (linear form) ===");
        System.out.println("\u001B[31m" + ast + "\u001B[0m\n");

        String fullAssembly = buildFullAssembly(ast);
        System.out.println("=== Compilation Output ===");
        System.out.println("\u001B[36m" + buildFullAssembly(ast) + "\u001B[0m");

        writeOutput(fullAssembly);
    }

    /* == Private interface == */
    private void writeOutput(String output) {
        try(FileWriter writer = new FileWriter("result.asm")) {
            writer.write(output);
            writer.write("\n");
        }
        catch(IOException e) {
            logger.logFatalErr(e.getMessage());
        }
    }

    private String buildFullAssembly(Expr ast) {
        return """
        section .note.GNU-stack noalloc noexec
        
        global main
        extern printf
        extern exit
        
        section .data
        fmt: db "Evaluation result: %d", 0xa, 0x00
        div_zero_msg: db "Error: division by zero", 0xa, 0x00
        
        section .text
        main:
        push rbp
        mov rbp, rsp
        
        """
        +
        ast.genx64()
        +
        """
        
        mov rdi, fmt
        mov rsi, rax
        xor eax, eax
        call printf
        
        xor edi, edi
        call exit
        
        div_by_zero:
        and rsp, -16
        mov rdi, div_zero_msg
        xor eax, eax
        call printf
        mov edi, 1
        call exit
        """;
    }
}
