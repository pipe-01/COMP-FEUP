package pt.up.fe.comp.jasmin;

import com.javacc.parser.tree.Literal;
import com.javacc.parser.tree.ObjectType;
import com.sun.jdi.ObjectReference;
import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminUtils;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.*;
import java.util.stream.Collectors;

public class OllirToJasmin {
    
    private final ClassUnit classUnit;
    private String superQualifiedName;
    private int lthBranch;
    private final HashMap<String, Integer> limitStack;
    private int instLimit;

    public OllirToJasmin(ClassUnit classUnit) {
        this.classUnit = classUnit;
        this.superQualifiedName = "";
        this.lthBranch = 0;
        limitStack = new HashMap<>();
    }

    public String getFullyQualifiedName(String className){
        for (var imp : classUnit.getImports()){
            var splittedImport = imp.split("\\.");
            String lastName;
            if(splittedImport.length == 0){
                lastName = imp;
            }
            lastName = splittedImport[splittedImport.length - 1];

            if(lastName.equals(className)){
                return imp.replace('.', '/');
            }
        }
        throw new RuntimeException("Could not find import for class " + className);
    }

    public String getCode(){

        var code = new StringBuilder();

        this.superQualifiedName = "java/lang/Object";
        if(classUnit.getSuperClass() != null) {
            this.superQualifiedName = getFullyQualifiedName(classUnit.getSuperClass());
        }

        code.append(".class public ").append(classUnit.getClassName()).append("\n");
        code.append(".super ").append(this.superQualifiedName).append("\n");


        if(!classUnit.getFields().isEmpty()) {
            for (var field : classUnit.getFields()) {
                code.append(getCode(field));
            }
        }


        String defaultContructor = ".method public <init>()V\n" +
                "   aload_0\n" +
                "   invokenonvirtual " + this.superQualifiedName +"/<init>()V\n" +
                "   return\n" +
                ".end method\n";

        //add default constructor
        code.append(defaultContructor);

        //add code for methods
        for (var method : classUnit.getMethods()){
            if(!method.isConstructMethod()) {
                code.append(getCode(method));
            }
        }

        return code.toString();
    }

    private String getCode(Field field) {
        var code = new StringBuilder();
        code.append(".field ");
        if(field.getFieldAccessModifier() != AccessModifiers.DEFAULT){
            code.append(field.getFieldAccessModifier().toString().toLowerCase()).append(" ");
        }
        else{
            code.append("public ").append(" ");
        }
        if(field.isFinalField()){
            code.append("final ");
        }
        if(field.isStaticField()){
            code.append("static ");
        }
        code.append(field.getFieldName()).append(" ");
        code.append(this.getJasminType(field.getFieldType()));
        code.append("\n");
        return code.toString();
    }

    public String getCode(Method method){
        var code = new StringBuilder();
        int locals = method.getVarTable().size();
        limitStack.put(method.getMethodName(), 0);

        code.append(".method ");
        if(!method.getMethodAccessModifier().equals(AccessModifiers.DEFAULT)){
            code.append(method.getMethodAccessModifier().name().toLowerCase()).append(" ");
        }

        if(method.isStaticMethod()){
            code.append("static ");
        }


        if(method.isFinalMethod()){
            code.append("final ");
        }

        code.append(method.getMethodName()).append("(");

        var paramTypes = method.getParams().stream().map(element -> getJasminType(element.getType())).collect(Collectors.joining());

        code.append(paramTypes).append(")").append(getJasminType(method.getReturnType())).append("\n");

        var auxCode = new StringBuilder();
        if(method.getVarTable().get("this") == null && !method.getMethodName().equals("main")){
            locals++;
        }
        auxCode.append(".limit locals ").append(locals).append("\n");

        for (Instruction instruction : method.getInstructions()){
            instLimit = 0;
            instruction.show();
            String instructionCode = this.generateInstruction(method, instruction, false);

            if(instLimit > limitStack.get(method.getMethodName())){
                limitStack.replace(method.getMethodName(), instLimit);
            }

            String [] lines = instructionCode.split("\n");
            for (String line : lines){
                if(line.length() > 0){
                    auxCode.append("\t").append(line).append("\n");
                }
            }
        }

        int lastIndex = method.getInstructions().size()-1;
        if(lastIndex < 0 || method.getInstructions().get(lastIndex).getInstType() != InstructionType.RETURN){
            auxCode.append("\treturn\n");
        }

        auxCode.append(".end method\n");
        code.append(".limit stack ").append(limitStack.get(method.getMethodName())).append("\n");
        code.append(auxCode);
        return code.toString();
    }

    private String generateInstruction(Method method, Instruction instruction, boolean inAssign) {
        var code = new StringBuilder();

        for (Map.Entry<String, Instruction> entry : method.getLabels().entrySet()){
            if(entry.getValue().equals(instruction)){
                code.append(processLabelName(entry.getKey())).append(":\n");
            }
        }

        switch (instruction.getInstType()){
            case ASSIGN:
                AssignInstruction assignInstruction = (AssignInstruction) instruction;
                code.append(this.generateAssign(method, assignInstruction));
                break;
            case CALL:
                CallInstruction callInstruction = (CallInstruction) instruction;
                code.append(this.generateCall(method, callInstruction));
                if(!inAssign && !callInstruction.getReturnType().getTypeOfElement().equals(ElementType.VOID)){
                    code.append("pop\n");
                }
                break;
            case BRANCH:
                CondBranchInstruction condBranchInstruction = (CondBranchInstruction) instruction;
                code.append(this.generateCondBranch(method, condBranchInstruction));
                break;
            case GOTO:
                GotoInstruction gotoInstruction = (GotoInstruction) instruction;
                code.append(this.generateGoto(gotoInstruction));
                break;
            case RETURN:
                ReturnInstruction returnInstruction = (ReturnInstruction) instruction;
                code.append(this.generateReturn(method, returnInstruction));
                break;
            case GETFIELD:
                GetFieldInstruction getFieldInstruction = (GetFieldInstruction) instruction;
                code.append(this.generateGetField(method, getFieldInstruction));
                break;
            case PUTFIELD:
                PutFieldInstruction putFieldInstruction = (PutFieldInstruction) instruction;
                code.append(this.generatePutField(method, putFieldInstruction));
                break;
            case BINARYOPER:
                BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) instruction;
                code.append(this.generateBinaryOp(method, binaryOpInstruction));
                break;
            case NOPER:
                instLimit++;
                SingleOpInstruction noOperInstruction = (SingleOpInstruction) instruction;
                code.append(this.generateNoOper(method, noOperInstruction));
                break;
            default:
                System.out.println(instruction.getInstType());
        }

        return code.toString();
    }

    private String generateNoOper(Method method, SingleOpInstruction noOperInstruction) {
        var code = new StringBuilder();
        Element element = noOperInstruction.getSingleOperand();
        code.append(getElement(method, element));
        return code.toString();
    }

    private String generateBinaryOp(Method method, BinaryOpInstruction binaryOpInstruction) {
        var code = new StringBuilder();

        instLimit += 2;

        OperationType operationType = binaryOpInstruction.getOperation().getOpType();
        List<Element> operands = new ArrayList<>();
        operands.add(binaryOpInstruction.getLeftOperand());
        operands.add(binaryOpInstruction.getRightOperand());

        if(operationType != OperationType.NOTB ){
            for (Element operand : operands){
                code.append(getElement(method, operand));
            }
        }
        else{
            code.append(getElement(method, operands.get(0)));
        }

        Element leftOperand = operands.get(0);
        Element rightOperand = operands.get(1);

        if(operationType == OperationType.LTH){
            if(leftOperand.isLiteral() && rightOperand.isLiteral()){ //ex.: 5  < 7
                int leftValue = Integer.parseInt(((LiteralElement) leftOperand).getLiteral());
                int rightValue = Integer.parseInt(((LiteralElement) leftOperand).getLiteral());

                if(leftValue < rightValue){
                    return "iconst_1\n";
                }
                else{
                    return "iconst_0\n";
                }
            }

            if(rightOperand.isLiteral() && ((LiteralElement) rightOperand).getLiteral().equals("0")){ // x < 0

                instLimit--;
                if(leftOperand.isLiteral() && ((LiteralElement) leftOperand).getLiteral().equals("0")){ // 0 < 0
                    return "iconst_0";
                }

                code = new StringBuilder();
                code.append(getElement(method, leftOperand));
                code.append("ifge ");
            }
            else if(leftOperand.isLiteral() && ((LiteralElement)leftOperand).getLiteral().equals("0")){ // 0 < x
                instLimit--;
                if(rightOperand.isLiteral() && ((LiteralElement) rightOperand).getLiteral().equals("0")){ // 0 < 0
                    return "iconst_0";
                }
                code = new StringBuilder();
                code.append(getElement(method, rightOperand));
                code.append("ifle ");
            }
            else{

                code.append("if_icmpge ");
            }
            code.append(lthBranch++).append("\niconst_1\ngoto ").append(lthBranch++).append("\n");
            code.append(lthBranch-2).append(": iconst_0\n").append(lthBranch-1).append(": ");
            return code.toString();
        }
        else if(operationType == OperationType.NOTB){
            instLimit--;
            if(leftOperand.isLiteral()){
                if(((LiteralElement) leftOperand).getLiteral().equals("0")){
                    return "iconst_1";
                }
                else{
                    return "iconst_0";
                }
            }
            code.append("ifne ").append(lthBranch++).append("\niconst_1\ngoto ").append(lthBranch++).append("\n");
            code.append(lthBranch-2).append(": iconst_0\n").append(lthBranch-1).append(": ");
            return code.toString();
        }
        else if(operationType == OperationType.ANDB){
            if(leftOperand.isLiteral()){
                if(rightOperand.isLiteral()){
                    if(((LiteralElement) rightOperand).getLiteral().equals("0")){
                        return "iconst_0\n";
                    }
                    else if(((LiteralElement) leftOperand).getLiteral().equals("1")){
                        return "iconst_1\n";
                    }
                }
                if(((LiteralElement) leftOperand).getLiteral().equals("0")){
                    return "iconst_0\n";
                }
            }
            else if(rightOperand.isLiteral()){
                if(((LiteralElement) rightOperand).getLiteral().equals("0")){
                    return "iconst_0\n";
                }
            }
            code.append("ifeq ").append(lthBranch).append("\n").append(getElement(method, operands.get(1))).append("\nifeq ").append(lthBranch++);
            code.append("\niconst_1\ngoto ").append(lthBranch++).append("\n").append(lthBranch-2).append(": iconst_0\n").append(lthBranch-1).append(": ");
            return code.toString();
        }

        code.append(convertTypeToInst(operands.get(0).getType().getTypeOfElement()));
        String auxOp = "";
        switch (binaryOpInstruction.getOperation().getOpType()){
            case ADD:
                auxOp = "add";
                break;
            case SUB:
                auxOp = "sub";
                break;
            case MUL:
                auxOp = "mul";
                break;
            case DIV:
                auxOp = "div";
                break;
        }
        code.append(auxOp).append("\n");

        return code.toString();
    }

    private String generateGetField(Method method, GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();
        instLimit += 2;
        code.append(getElement(method, getFieldInstruction.getFirstOperand()));
        code.append("getfield ").append(classUnit.getClassName()).append("/");
        code.append(((Operand) getFieldInstruction.getSecondOperand()).getName()).append(" ").append(getJasminType(getFieldInstruction.getSecondOperand().getType())).append("\n");
        return code.toString();
    }

    private String generatePutField(Method method, PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();
        instLimit += 2;
        code.append(getElement(method, putFieldInstruction.getFirstOperand()));
        code.append(getElement(method, putFieldInstruction.getThirdOperand()));
        code.append("putfield ").append(classUnit.getClassName()).append("/").append(((Operand) putFieldInstruction.getSecondOperand()).getName()).append(" ");
        code.append(getJasminType(putFieldInstruction.getSecondOperand().getType())).append("\n");
        return code.toString();
    }



    private String generateReturn(Method method, ReturnInstruction returnInstruction) {
        var code = new StringBuilder();
        instLimit += 1;
        if(returnInstruction.hasReturnValue()){
            Element element = returnInstruction.getOperand();
            code.append(getElement(method, element));
            String typeStr = convertTypeToInst(element.getType().getTypeOfElement());
            code.append(typeStr);
        }
        code.append("return\n");

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInstruction) {
        var code = new StringBuilder();
        code.append("goto ").append(gotoInstruction.getLabel()).append("\n");
        return code.toString();
    }

    private String generateCondBranch(Method method, CondBranchInstruction branchInstruction) {
        var code = new StringBuilder();
        instLimit++;

        code.append(getElement(method, branchInstruction.getOperands().get(0)));

        String firstLabel = processLabelName(branchInstruction.getLabel());
        branchInstruction.show();
        //OperationType operationType = ((OpInstruction)branchInstruction.getCondition()).getOperation().getOpType();

        /*
        if(operationType != OperationType.NOTB && operationType != OperationType.ANDB && operationType != OperationType.OR){
            instLimit++;
            code.append(getElement(method, branchInstruction.getOperands().get(1)));
        }
        */

        code.append("ifeq ").append(firstLabel).append("\n");
        /*
        switch (operationType){
            case ANDB:
                code.append("ifeq ").append(firstLabel).append("\n");
                code.append(getElement(method, branchInstruction.getOperands().get(1)));
                code.append("ifeq ").append(firstLabel).append("\n");
                break;
            case LTH:
                Element leftOperand = branchInstruction.getOperands().get(0);
                Element rightOperand = branchInstruction.getOperands().get(1);

                if(rightOperand.isLiteral() && ((LiteralElement) rightOperand).getLiteral().equals("0")){
                    instLimit--;
                    code = new StringBuilder();
                    code.append(getElement(method, leftOperand));
                    code.append("ifge ");
                }
                else if(leftOperand.isLiteral() && ((LiteralElement) leftOperand).getLiteral().equals("0")){
                    instLimit--;
                    code = new StringBuilder();
                    code.append(getElement(method, rightOperand));
                    code.append("ifle ");
                }
                else{
                    code.append("if_icmpge ");
                }
                code.append(firstLabel).append("\n");
                break;
            case NOTB:
                code.append("ifne ").append(firstLabel).append("\n");
                break;
        }

         */

        return code.toString();
    }

    private String generateAssign(Method method, AssignInstruction assignInstruction) {
        var code = new StringBuilder();
        Operand leftOp = (Operand) assignInstruction.getDest();

        if(assignInstruction.getRhs().getInstType() == InstructionType.CALL && ((CallInstruction)(assignInstruction.getRhs())).getInvocationType() == CallType.NEW){ //Assign with 'new'
            CallInstruction callInstruction = (CallInstruction) assignInstruction.getRhs();
            if(callInstruction.getReturnType().getTypeOfElement() == ElementType.ARRAYREF){
                instLimit++;
                code.append(getElement(method, callInstruction.getListOfOperands().get(0)));
                code.append("\nnewarray int\n");
                code.append(createStoreInst(ElementType.ARRAYREF, false, getVirtualReg(method, leftOp))).append("\n");
            }
            else{
                instLimit += 2;
                code.append("new ").append(((ClassType)callInstruction.getReturnType()).getName()).append("\n");
                code.append("dup ").append("\n");
            }
        }
        else{
            Instruction rightSide = assignInstruction.getRhs();
            //check increment cases --> ex.: x = x + 1
            String checkIncrement = this.checkIncrementAssign(method, leftOp, rightSide);
            if(checkIncrement != null){
                return checkIncrement;
            }

            boolean isArrayAssign = false;

            if(leftOp instanceof ArrayOperand){
                instLimit += 2;
                isArrayAssign = true;
                code.append(getElementArrayAssign(method, assignInstruction.getDest()));
            }
            code.append(this.generateInstruction(method, assignInstruction.getRhs(), true));
            code.append(createStoreInst(leftOp.getType().getTypeOfElement(), isArrayAssign, getVirtualReg(method, leftOp))).append("\n");
        }
        return code.toString();
    }

    private String getElementArrayAssign(Method method, Element element) {
        var code = new StringBuilder();
        ElementType elementType = element.getType().getTypeOfElement();

        code.append(createLoadInst(elementType, true, getVirtualReg(method, (Operand) element))).append("\n");
        code.append(getElement(method, ((ArrayOperand) element).getIndexOperands().get(0)));

        return code.toString();
    }

    private String checkIncrementAssign(Method method, Operand leftOp, Instruction rightSide) {
        var code = new StringBuilder();
        if(leftOp.getType().getTypeOfElement() != ElementType.INT32 || leftOp instanceof ArrayOperand || rightSide.getInstType() != InstructionType.BINARYOPER){
            return null;
        }

        BinaryOpInstruction rightSideOp = (BinaryOpInstruction) rightSide;
        OperationType operationType = ((BinaryOpInstruction) rightSide).getOperation().getOpType();

        if(operationType != OperationType.ADD && operationType != OperationType.SUB){
            return null;
        }

        Element leftElement = rightSideOp.getLeftOperand();
        Element rightElement = rightSideOp.getRightOperand();

        if(leftElement.getType().getTypeOfElement() != ElementType.INT32 || rightElement.getType().getTypeOfElement() != ElementType.INT32){
            return null;
        }

        int incrementValue;

        if(leftElement instanceof Operand && rightElement.isLiteral()){
            incrementValue = Integer.parseInt(((LiteralElement) rightElement).getLiteral());
            if(operationType == OperationType.SUB){
                incrementValue *= -1;
            }
            if(!((Operand)leftElement).getName().equals(leftOp.getName())){
                return null;
            }
        }
        else if(rightElement instanceof Operand && leftElement.isLiteral()){
            incrementValue = Integer.parseInt(((LiteralElement) leftElement).getLiteral());
            if(operationType == OperationType.SUB){
                return null;
            }
            if(!((Operand)rightElement).getName().equals(leftOp.getName())){
                return null;
            }
        }
        else{
            return null;
        }

        if(incrementValue > 127 || incrementValue < -128){
            return null;
        }

        code.append("iinc ").append(getVirtualReg(method, leftOp)).append(" ").append(incrementValue).append("\n");

        return code.toString();
    }

    private String generateCall(Method method, CallInstruction callInstruction) {
        var code = new StringBuilder();

        switch (callInstruction.getInvocationType()){
            case invokestatic:
                code.append(this.generateInvokeStatic(method, callInstruction));
                break;
            case invokevirtual:
                code.append(this.generateInvokeVirtual(method, callInstruction));
                break;
            case invokespecial:
                code.append(this.generateInvokeSpecial(method, callInstruction));
                break;
            case arraylength:
                instLimit++;
                code.append(createLoadInst(callInstruction.getFirstArg().getType().getTypeOfElement(),true, getVirtualReg(method, (Operand) callInstruction.getFirstArg()))).append("\narraylength\n");
                break;
            case invokeinterface:
            case NEW:
            case ldc:
                break;
            default:
                throw new RuntimeException("Invalid call type!");
        }
        return code.toString();
    }

    private String generateInvokeSpecial(Method method, CallInstruction callInstruction) {
        var code = new StringBuilder();

        StringBuilder params = new StringBuilder();
        for(Element param : callInstruction.getListOfOperands()) {
            params.append(getJasminType(param.getType()));
            code.append(getElement(method, param));
        }

        code.append("invokespecial ").append(((ClassType)callInstruction.getFirstArg().getType()).getName())
                .append(".<init>(");

        code.append(params);

        code.append(")V\n");
        code.append(createStoreInst(ElementType.CLASS, false, getVirtualReg(method, (Operand) callInstruction.getFirstArg())));

        instLimit += Math.max(callInstruction.getListOfOperands().size(), 1);

        return code.toString();
    }

    private String generateInvokeVirtual(Method method, CallInstruction callInstruction) {
        var code = new StringBuilder();
        Operand firstArg = (Operand) callInstruction.getFirstArg();
        LiteralElement methodElement = (LiteralElement) callInstruction.getSecondArg();

        code.append(getElement(method, callInstruction.getFirstArg()));

        StringBuilder params = new StringBuilder();
        for(Element param : callInstruction.getListOfOperands()) {
            params.append(getJasminType(param.getType()));
            code.append(getElement(method, param));
        }

        code.append("invokevirtual ").append(getMethodName(firstArg, methodElement)).append("(").append(params).append(")").append(getJasminType(callInstruction.getReturnType())).append("\n");

        instLimit += 1 + callInstruction.getListOfOperands().size();

        return code.toString();
    }

    private String generateInvokeStatic(Method method, CallInstruction callInstruction) {
        var code = new StringBuilder();
        Operand firstArg = (Operand) callInstruction.getFirstArg();
        LiteralElement methodElement = (LiteralElement) callInstruction.getSecondArg();

        var params = new StringBuilder();

        for (Element param : callInstruction.getListOfOperands()){
            params.append(getJasminType(param.getType()));
            code.append(getElement(method, param));
        }

        code.append("invokestatic ").append(getMethodName(firstArg, methodElement)).append("(").append(params).append(")").append(getJasminType(callInstruction.getReturnType())).append("\n");

        int voidReturn = 0;
        if(callInstruction.getReturnType().getTypeOfElement() != ElementType.VOID){
            voidReturn = 1;
        }
        instLimit += Math.max(voidReturn, callInstruction.getListOfOperands().size());

        return code.toString();
    }

    private String createStoreInst(ElementType type, boolean isArray, int virtualReg) {
        var code = new StringBuilder();
        code.append(convertTypeToInst(type));
        if(isArray){
            code.append("a");
        }
        code.append("store");

        if(!isArray) {
            if(virtualReg <= 3){
                code.append("_");
            }
            else{
                code.append(" ");
            }
            code.append(virtualReg);
        }
        return code.toString();
    }


    private String getElement(Method method, Element operand) {
        var code = new StringBuilder();
        boolean isArray = operand instanceof ArrayOperand;
        ElementType elementType = operand.getType().getTypeOfElement();

        if(operand.isLiteral()){
            int value = Integer.parseInt(((LiteralElement) operand).getLiteral());
            code.append(createConstInst(value));
        }
        else{
            if(operand.getType().getTypeOfElement() != ElementType.CLASS){
                if(isArray){
                    instLimit++;
                    code.append(createLoadInst(elementType, isArray, getVirtualReg(method, (Operand) operand))).append("\n");
                    code.append(getElement(method, ((ArrayOperand) operand).getIndexOperands().get(0))).append("\n");
                    code.append(convertTypeToInst(elementType)).append("aload\n");
                }
                else {
                    code.append(createLoadInst(elementType, isArray, getVirtualReg(method, (Operand) operand)));
                }
            }
        }
        code.append("\n");
        return code.toString();
    }

    private String getMethodName(Operand operand, LiteralElement methodElement) {
        String methodName = methodElement.getLiteral().replaceAll("\"", "");
        if(operand.getName().equals("this")) {
            return classUnit.getClassName() + '/' + methodName;
        } else if(operand.getType().getTypeOfElement() == ElementType.CLASS) { //static invoke
            return operand.getName() + '/' + methodName;
        } else {
            return ((ClassType)operand.getType()).getName() + '/' + methodName;
        }
    }

    private String createConstInst(int val){
        if(val == -1){
            return "iconst_m1";
        }
        else if (val >= 0 && val <= 5){
            return "iconst_" + val;
        }
        else if(val >= -128 && val <= 127){
            return "bipush " + val;
        }
        else if(val >= -32768 && val <= 32767){
            return "sipush " + val;
        }
        else{
            return "ldc " + val;
        }
    }

    private int getVirtualReg(Method method, Operand operand) {
        if(operand.getName().equals("this")){
            return 0;
        }
        return method.getVarTable().get(operand.getName()).getVirtualReg();
    }

    private String createLoadInst(ElementType type, boolean isArray, int virtualReg) {
        StringBuilder code = new StringBuilder();
        if(isArray){
            code.append("a").append("load");
        }
        else {
            code.append(convertTypeToInst(type)).append("load");
        }

        if(virtualReg <= 3){
            code.append("_");
        }
        else{
            code.append(" ");
        }

        code.append(virtualReg);

        return code.toString();
    }

    private static String convertTypeToInst(ElementType type) {
        switch (type) {
            case INT32:
            case BOOLEAN:
                return "i";
            case ARRAYREF:
            case OBJECTREF:
            case THIS:
            case CLASS:
                return "a";
            default :
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    /*Making sure labels have not the same name as Jasmin generated labels (not and lth)*/
    static String processLabelName(String labelName) {

        if(labelName.matches("^-?\\d+$")) //label name is integer (can be the same name as a generated label, so we add a character)
            labelName += "_";
        return labelName;
    }

    private String getJasminReturnType(Method method, Type type) {
        switch (type.getTypeOfElement()) {
            case INT32:
                return "I";
            case BOOLEAN:
                return "Z";
            case ARRAYREF:
                return "[I";
            case OBJECTREF:
                return method.getMethodName();
            case VOID:
                return "V";
            default:
                return "";
        }
    }

    public String getJasminType(Type type){
        switch (type.getTypeOfElement()){
            case INT32:
                return "I";
            case BOOLEAN:
                return "Z";
            case ARRAYREF:
                return "[" + getJasminType(((ArrayType) type).getTypeOfElements());
            case CLASS:
            case OBJECTREF:
                return "L" + ((ClassType) type).getName() + ";";
            case THIS:
                return classUnit.getClassName();
            case STRING:
                return "Ljava/lang/String;";
            case VOID:
                return "V";
            default:
                return "";
        }

    }

    public String getJasminType(ElementType type) {
        switch (type) {
            case STRING:
                return "Ljava/lang/String;";
            case VOID:
                return "V";
            case BOOLEAN:
                return "Z";
            case INT32:
                return "I";
            default:
                return "";
        }
    }
}
