# Expression Compiler

A small compiler, written in Java, that turns integer arithmetic and bitwise-logic expressions into x86-64 assembly (NASM syntax). It runs as an interactive REPL: you type an expression and it shows each stage of compilation (the tokens, the parsed tree and the generated assembly). It also writes a complete assembly program to `result.asm`. When you assemble, link and run that program, it prints the expression's value.

```x86asm
> (2 + 3) * 4 - 6 / 2
=== Lexer output ===
Token: "(", kind: LEFT_PAREN
Token: "2", kind: INT_LIT
...
=== Parser output (linear form) ===
(((2 ADD_OP 3) MUL_OP 4) SUB_OP (6 DIV_OP 2))

=== Compilation Output ===
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

mov rax, 2
push rax
mov rax, 6
pop rbx
test rbx, rbx
jz div_by_zero
xor rdx, rdx
div rbx
push rax
mov rax, 2
push rax
mov rax, 3
pop rbx
add rax, rbx
push rax
mov rax, 4
pop rbx
imul rax, rbx
pop rbx
sub rax, rbx

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
```

```
$ ./result
Evaluation result: 17
```

## Supported language

| Operator | Meaning              | Precedence  |
|----------|----------------------|-------------|
| `( )`    | grouping             | highest     |
| `~`      | bitwise NOT (unary)  |             |
| `* /`    | multiply, divide     |             |
| `+ -`    | add, subtract        |             |
| `&`      | bitwise AND          |             |
| `\|`     | bitwise OR           | lowest      |

* Operands are non-negative integer literals (`[0-9]+`). There is no unary minus.
* Binary operators associate to the left.
* Whitespace is ignored. Any other character is a fatal lexer error.

### Grammar (as implemented by the parser)

```ebnf
expr      ::= or
or        ::= and   ( "|" and )*
and       ::= not   ( "&" and )*
not       ::= "~" not | sum_diff
sum_diff  ::= prod_quot ( ("+" | "-") prod_quot )*
prod_quot ::= primary   ( ("*" | "/") primary )*
primary   ::= "(" expr ")" | "~" primary | int
int       ::= [0-9]+
```

> The older grammar in [`src/Compiler/models/Parser/grammar.txt`](src/Compiler/models/Parser/grammar.txt) predates the current parser. It splits arithmetic and boolean expressions apart, but the parser treats them as a single expression language.

## Requirements

* **JDK 17+**, needed to build and run the compiler (the code uses text blocks).
* **NASM** and **GCC** (or another linker that pulls in libc) to assemble and run the generated program on x86-64 Linux.

## Building and running

### From the command line

```bash
# Compile
javac -d out $(find src -name "*.java")

# Start the REPL
java -cp out Compiler.Main
```

Type an expression at the `>` prompt. Every expression you enter overwrites `result.asm` in the current working directory. Press `Ctrl+C` to quit.

### Running the generated assembly

```bash
nasm -f elf64 result.asm -o result.o
gcc -no-pie result.o -o result
./result
```

The program's entry point is `main`, and it calls `printf` and `exit` from libc. That is why it is linked through `gcc`, not plain `ld`.

## How it works

```
source text ──► Lexer ──► tokens ──► Parser ──► AST ──► CodeGen ──► x86-64 asm
```

1. **Lexer** ([`Lexer.java`](src/Compiler/models/Lexer/Lexer.java)) scans the input one character at a time. It produces `INT_LIT` tokens for runs of digits and a token for each operator or parenthesis, and it adds an `EOF` token at the end.
2. **Parser** ([`Parser.java`](src/Compiler/models/Parser/Parser.java)) is a recursive-descent parser with one method per precedence level. It builds an AST of three node types: `Int`, `BinaryOp` and `UnaryOp` ([`Ast/Impls`](src/Compiler/models/Parser/Ast/Impls)).
3. **Code generation** ([`CodeGen.java`](src/Compiler/models/CodeGen/CodeGen.java)) is a simple stack-machine scheme. Every node leaves its result in `rax`. For a binary operation, one operand is evaluated and pushed, the other is evaluated into `rax`, and the first is popped into `rbx` before the operator is applied. Subtraction and division evaluate their operands in reverse order, so the left operand ends up in `rax`.
4. **Output** ([`CompilerView.java`](src/Compiler/views/CompilerView.java)) wraps the generated instructions in a `main` function. That function passes `rax` to `printf` and then calls libc's `exit` with status 0. Before each division, the program checks whether the divisor is zero. If it is, the program prints `Error: division by zero` and exits with status 1. The finished program is printed and written to `result.asm`.

### Project layout

```
src/Compiler/
├── Main.java                  # entry point; starts the REPL
├── controller/Repl.java       # read-eval-print loop
├── views/CompilerView.java    # prints each stage, builds and writes result.asm
└── models/
    ├── Lexer/                 # Lexer, Token, TokenKind
    ├── Parser/                # Parser, AST node types, grammar notes
    ├── CodeGen/               # AST → x86-64 instruction emitters
    └── Logger/                # fatal-error logging (prints in red and exits)
```

## Known limitations

* **Errors are fatal.** An invalid character or a syntax error ends the whole REPL session; it does not just reject the line. An empty line is also fatal.
* **Division is unsigned** (`div`).
* **Literals must fit in a Java `int`.** Larger literals crash the parser.
