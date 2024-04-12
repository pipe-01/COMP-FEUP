# Compilers Project


Diogo Maia, up201904974

Filipe Pinto, up201809590

Diogo Moreira, up201804904



## SUMMARY:
Our tool creates an compiled Java class from a .jmm file
To do it, he does the following:
* Parses the jmm input
* Creates an AST from the parsed result
* Creates a symbol table from such AST and semantic analyses it
* Creates ollir code from the AST and the symbol table
* Creates jasmin code from the ollir code


## SEMANTIC ANALYSIS:
In the semantic analysis, our tool verifies if:
* Variable names used in the code have a corresponding declaration, either as a local variable, a method parameter, or a field of the class (if applicable).
* Operands of an operation must types compatible with the operation
* Array cannot be used in arithmetic operations
* Array access index is an expression of type integer
* The type of the assignee must be compatible with the assigned
* Expressions in conditions must return a boolean
* When calling methods of the class declared in the code, the types of arguments of the call are compatible with the types in the method declaration
* In case the method does not exist, the class extends another class and reports an error if it does not
* When calling methods that belong to other classes other than the class declared in the code, the classes are being imported

## CODE GENERATION:

### OLLIR
The ollir basic structure is generated from the symbol table and the AST.
It uses an AJmmVisitor to go from the got through all the nodes of the AST and from the bottom to the top, from left to right.
It generates a temporary variable for each operation from array indexation to adding and comparing
For example, something like 'a = 3+4', being 'a' a class field will be something like (we will exclude typing to simplify): t0 := 3+4; putfield(a, t0);



### Jasmin
All the jasmin basic structure is being generated including the constructor, fields, and methods.

Taking the previously generated `OLLIR` code, parsed using the provided `OLLIR` tool, we take its output (ClassUnit object), use it to generate the jasmin code.
We make use of the variable table containing both all the registers and the list of instructions of each method to select the corresponding JVM instructions.

We have a default template constructor which is filled with the super qualified name of the current class.

For each method present in class, the "limit stack" and "limit locals" are being calculated dynamically. The approach for defining each one of them is well-defined:

- The "limit locals" is initialized by the number of local variables present in Var Table. In case the method isn't static "limit locals" is incremented by 1. This number stands for the register 0 which is allocated by the instance of the class: `this`.

- The "limit stack" is calculated with a Hash Map with key = name of the method and a value = Integer which represents the required stack size. This number is incremented or decremented whenever there is a push or a pop into the stack, respectively. The required stack size is calculated for each instruction, and the maximum stack size in a method is selected as the stack limit.


Lower cost instructions are selected in multiple cases, such as `iinc` instructions on variable increments, for example : `(i = i - 10)`, instead of `iadd` and `isub`, the use of `if\<cond\>` on comparisons with 0
and also the different constant loading instructions such as: `iconst_m1`, `iconst`, `bipush`, `sipush`, `ldc`, depending on the constant value.


As an extra optimization to stack size and operations, some operations are replaced by their results in the generation. When generating `LTH` operations with both operands being literals, the result is pushed into the stack, for example, `x = 0 < 1` will result in `iconst_1` followed by `istore_1` (assuming 1 is the register of `x`).


## PROS:
It works in most cases.

## CONS: 
The ollir generator has currently a problem regarding the calling of external methods by calling a void method.
We have noticed our jasmin has a problem when working with labels. We could not identify the problem on time.


### Notes - Evaluation

The test "testStmtEmptyScope()" does not work since we have do not create nodes on the AST for scope blocks since those don't exist on the current project's language.
For that matter, when the parser reads "{}" and because the token "statement" is #void, we retrieve an empty AST resulting on a NullPointException
However, in our language we don't have the need to read scopes then we only place in the AST the statements inside them.
