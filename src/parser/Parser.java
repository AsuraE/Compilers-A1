package parser;

import java.io.IOException;
import java.util.ArrayList;

import source.ErrorHandler;
import source.Errors;
import source.Position;
import syms.Predefined;
import syms.Scope;
import syms.SymEntry;
import syms.SymbolTable;
import syms.Type;
import tree.ConstExp;
import tree.DeclNode;
import tree.ExpNode;
import tree.Operator;
import tree.StatementNode;

/**
 * class Parser - PL0 recursive descent parser. To understand how this parser
 *  works read the notes on recursive descent parsing.
 * @version $Revision: 22 $  $Date: 2014-05-20 15:14:36 +1000 (Tue, 20 May 2014) $ 
 *
 *  The syntax analyzer recognises a PL0 program according to the following
 *  syntax specification using a recursive descent parser. It constructs
 *  the corresponding abstract syntax tree and skeleton symbol table.
 *  PL0 EBNF Grammar:
 *  Program -> Block ENDOFFILE
 *  Block -> { Declaration } CompoundStatement
 *  Declaration -> ConstDefList | TypeDefList | VarDeclList | ProcedureDef
 *  ConstDefList -> KW_CONST ConstDef { ConstDef }
 *  ConstDef -> IDENTIFIER EQUALS Constant SEMICOLON
 *  Constant -> NUMBER | IDENTIFIER | MINUS Constant
 *  TypeDefList -> KW_TYPE TypeDef { TypeDef }
 *  TypeDef -> IDENTIFIER EQUALS Type SEMICOLON
 *  Type -> TypeIdentifier | SubrangeType
 *  TypeIdentifier -> IDENTIFIER
 *  SubrangeType -> LBRACKET Constant RANGE Constant RBRACKET
 *  VarDeclList -> KW_VAR VarDecl { VarDecl }
 *  VarDecl -> IDENTIFIER COLON TypeIdentifier SEMICOLON
 *  ProcedureDef -> ProcedureHead EQUALS Block SEMICOLON
 *  ProcedureHead -> KW_PROCEDURE IDENTIFIER LPAREN FormalParameters RPAREN
 *  FormalParamters ->
 *  CompoundStatement -> KW_BEGIN StatementList KW_END
 *  StatementList -> Statement { SEMICOLON Statement }
 *  Statement -> WhileStatement | IfStatement | CallStatement | Assignment | 
 *               ReadStatement | WriteStatement | CompoundStatement | 
 *               SkipStatement | DoStatement
 *  SkipStatement -> KW_SKIP
 *  Assignment -> LValueList ASSIGN ConditionList
 *  LValueList -> LValue { COMMA LValue }
 *  ConditionList -> Condition { COMMA Condition }
 *  DoStatement -> KW_DO DoBranch { SEPARATOR DoBranch } KW_OD
 *  DoBranch -> Condition KW_THEN StatementList [ KW_EXIT ]
 *  WhileStatement -> KW_WHILE Condition KW_DO Statement
 *  IfStatement -> KW_IF Condition KW_THEN Statement KW_ELSE Statement
 *  CallStatement -> KW_CALL IDENTIFIER LPAREN ActualParameters RPAREN
 *  ActualParameters ->
 *  ReadStatement -> KW_READ LValue
 *  WriteStatement -> KW_WRITE Exp
 *  Condition -> Exp [ RelOp Exp ]
 *  RelOp   -> EQUALS | NEQUALS | LEQUALS | LESS | GREATER | GEQUALS
 *  Exp     -> [ PLUS | MINUS ] Term   { ( PLUS | MINUS ) Term }
 *  Term    -> Factor { ( TIMES | DIVIDE ) Factor }
 *  Factor  -> LPAREN Condition RPAREN | NUMBER | LValue
 *  LValue -> IDENTIFIER
 *
 *  where any constructs not defined by the above productions
 *  are terminal symbols generated by the lexical analyser.
 */
public class Parser {

    /***************** Start Sets for parsing rules **********************/

    /** Set of tokens that may start an LValue. */
    private final static TokenSet LVALUE_START_SET =
        new TokenSet( Token.IDENTIFIER );
    /** Set of tokens that may start a Statement. */
    private final static TokenSet STATEMENT_START_SET =
        LVALUE_START_SET.union( Token.KW_WHILE, Token.KW_IF,
          Token.KW_READ, Token.KW_WRITE,
          Token.KW_CALL, Token.KW_BEGIN, Token.KW_SKIP, Token.KW_DO );
    /** Set of tokens that may start a Declaration. */
    private final static TokenSet DECLARATION_START_SET =
        new TokenSet( Token.KW_CONST, Token.KW_TYPE, Token.KW_VAR, 
          Token.KW_PROCEDURE );
    /** Set of tokens that may start a Block. */
    private final static TokenSet BLOCK_START_SET =
        DECLARATION_START_SET.union( Token.KW_BEGIN );
    /** Set of tokens that may start a Constant. */
    private final static TokenSet CONSTANT_START_SET = 
        new TokenSet( Token.IDENTIFIER, Token.NUMBER, Token.MINUS );
    /** Set of tokens that may start a Type. */
    private final static TokenSet TYPE_START_SET = 
        new TokenSet( Token.IDENTIFIER, Token.LBRACKET );
    /** Set of tokens that may start a Factor. */
    private final static TokenSet FACTOR_START_SET = 
        LVALUE_START_SET.union( Token.NUMBER, Token.LPAREN );
    /** Set of tokens that may start a Term. */
    private final static TokenSet TERM_START_SET = 
        FACTOR_START_SET;
    /** Set of tokens that may start an Expression. */
    private final static TokenSet EXP_START_SET =
        TERM_START_SET.union( Token.PLUS, Token.MINUS );
    /** Set of tokens that may start a Condition. */
    private final static TokenSet CONDITION_START_SET =
        EXP_START_SET;

    /************ Operation sets for expressions ***************************/
    /** Set of tokens representing relational operators. */
    private final static TokenSet REL_OPS_SET =
        new TokenSet( Token.EQUALS, Token.NEQUALS, Token.LESS, Token.GREATER,
          Token.LEQUALS, Token.GEQUALS );
    /** Set of tokens for expression operators. */
    private final static TokenSet EXP_OPS_SET =
        new TokenSet( Token.PLUS, Token.MINUS );
    /** Set of tokens for term operators. */
    private final static TokenSet TERM_OPS_SET =
        new TokenSet( Token.TIMES, Token.DIVIDE );

    
    /*************************** Instance Variables ************************/
    /** The input token stream */
    private TokenStream tokens;
    /** The symbol table */
    private SymbolTable symtab;
    /** The object to report errors to */
    private Errors errors = ErrorHandler.getErrorHandler();
    
    /****************************** Constructor ****************************/
    /** Construct a parser with the given token stream 
     * @param tokens - stream of lexical tokens
     * @requires tokens != null;
     */
    public Parser( TokenStream tokens ) throws IOException {
        /** Set up an input token stream */
        this.tokens = tokens;
    }
    /***************************** Public Method ****************************/
    /** Parse the input stream. 
     *  @return constructed tree only if the stream was parsed correctly.
     */
    public StatementNode.ProgramNode parse() {
        StatementNode.ProgramNode program =  
                parseProgram( new TokenSet( Token.EOF ) );
        errors.flush();
        return program;
    }

    /**************************** Parsing Methods ***************************/

    /** RULE: Program -> Block ENDOFFILE */
    private StatementNode.ProgramNode parseProgram( TokenSet recoverSet ) {
        if( !tokens.beginRule( "Program", BLOCK_START_SET, recoverSet ) ) {
            return null;
        }
        assert tokens.isIn( BLOCK_START_SET );
        /** Set up a symbol table. 
         * The initial value includes the predefined scope.
         */
        symtab = new SymbolTable();
        SymEntry.ProcedureEntry proc = 
            symtab.getCurrentScope().addProcedure( "<main>", tokens.getPosn() );
        if( proc  == null ) {
            fatal( "Could not add main program to symbol table" );
        }
        Scope blockLocals = symtab.newScope( proc );
        proc.setLocalScope( blockLocals );
        StatementNode.BlockNode block = parseBlock( recoverSet );
        block.setBlockLocals( blockLocals );
        symtab.leaveScope();
        /* We can't use match because there is nothing following end of file */
        tokens.endRule( "Program", recoverSet );
        return new StatementNode.ProgramNode( block.getPosition(), 
                symtab, block );
    }
    /** RULE: Block -> { Declaration } CompoundStatement */
    private StatementNode.BlockNode parseBlock( TokenSet recoverSet ) {
        DeclNode.DeclListNode procedures = new DeclNode.DeclListNode();
        if( !tokens.beginRule("Block", BLOCK_START_SET, recoverSet)) {
            return new StatementNode.BlockNode( tokens.getPosn(), procedures, 
                    new StatementNode.ErrorNode( tokens.getPosn()) );
        }
        assert tokens.isIn( BLOCK_START_SET );
        while( tokens.isIn( DECLARATION_START_SET ) ) {
            procedures = parseDeclaration( procedures, 
                        recoverSet.union( BLOCK_START_SET ) );
        }
        StatementNode statements = parseCompoundStatement( recoverSet );
        tokens.endRule( "Block", recoverSet );
        return new StatementNode.BlockNode( statements.getPosition(),
                                    procedures, statements );
    }
    /** RULE:
     *  Declaration -> ConstDefList | TypeDefList | VarDeclList | ProcedureDef 
     */
    private DeclNode.DeclListNode parseDeclaration( 
            DeclNode.DeclListNode procedures, TokenSet recoverSet ) {
        assert tokens.isIn( DECLARATION_START_SET );
        tokens.beginRule( "Declaration", DECLARATION_START_SET ); /* cannot fail */
        if( tokens.isMatch( Token.KW_CONST ) ) {
            parseConstDefList( recoverSet );
        } else if( tokens.isMatch( Token.KW_TYPE ) ) {
            parseTypeDefList( recoverSet );
        } else if( tokens.isMatch( Token.KW_VAR ) ) {
            parseVarDeclList( recoverSet );
        } else if( tokens.isMatch( Token.KW_PROCEDURE ) ) {
            DeclNode.ProcedureNode proc = parseProcedureDef( recoverSet );
            procedures.addDeclaration( proc );
        } else { // cannot get here
            errors.fatal( "parseDeclaration", tokens.getPosn() );
        }
        tokens.endRule( "Declaration", recoverSet );
        return procedures;
    }
    /** Rule: ConstDefList -> KW_CONST ConstDef { ConstDef } */
    private void parseConstDefList( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_CONST );
        tokens.beginRule( "Constant Definition List", Token.KW_CONST ); /* can't fail */
        tokens.match( Token.KW_CONST );
        do {
            parseConstDef( recoverSet.union( Token.IDENTIFIER ) );
        } while( tokens.isMatch( Token.IDENTIFIER ) );
        tokens.endRule( "Constant Definition List", recoverSet );
    }
    /** Rule: ConstDef -> IDENTIFIER EQUALS Constant SEMICOLON */
    private void parseConstDef( TokenSet recoverSet ) {
        if( !tokens.beginRule("Constant Definition", Token.IDENTIFIER, recoverSet)){
            return;
        }
        assert tokens.isMatch( Token.IDENTIFIER );
        String name = tokens.getName();
        Position posn = tokens.getPosn();
        tokens.match( Token.IDENTIFIER );    /* cannot fail */
        tokens.match( Token.EQUALS, CONSTANT_START_SET );
        ConstExp tree = 
            parseConstant( recoverSet.union( Token.SEMICOLON ) );
        if( symtab.getCurrentScope().addConstant( name, posn, tree ) == null ) {
                errors.error( "Constant identifier " + name + 
                    " already declared in this scope", posn );
        }
        tokens.match( Token.SEMICOLON, recoverSet );
        tokens.endRule( "Constant Definition", recoverSet );
    }
    /** Rule: Constant -> NUMBER | IDENTIFIER | MINUS Constant */
    private ConstExp parseConstant( TokenSet recoverSet ) {
        if( !tokens.beginRule( "Constant", CONSTANT_START_SET, recoverSet ) ) {
            /* Defaults to error node on error */
            return new ConstExp.ErrorNode( tokens.getPosn(), 
                    symtab.getCurrentScope() );
        }
        assert tokens.isIn( CONSTANT_START_SET ); 
        ConstExp tree;  
        if( tokens.isMatch( Token.NUMBER ) ) {
            tree = new ConstExp.NumberNode( tokens.getPosn(), 
                     symtab.getCurrentScope(), Predefined.INTEGER_TYPE, 
                     tokens.getIntValue() );
            tokens.match( Token.NUMBER ); /* cannot fail */
        } else if( tokens.isMatch( Token.IDENTIFIER ) ) {
            tree = new ConstExp.ConstIdNode( tokens.getPosn(),
                    symtab.getCurrentScope(), tokens.getName());
            tokens.match( Token.IDENTIFIER ); /* cannot fail */
        } else if( tokens.isMatch( Token.MINUS ) ) {
            Position pos = tokens.getPosn();
            tokens.match( Token.MINUS ); /* cannot fail */
            tree = parseConstant( recoverSet );
            tree = new ConstExp.NegateNode( pos, 
                    symtab.getCurrentScope(), tree );
        } else {
            tree = null;
            fatal( "parseConstant" ); /* cannot get here */
        }
        tokens.endRule( "Constant", recoverSet );
        return tree;
    }
    /** Rule: TypeDefList -> KW_TYPE TypeDef { TypeDef }  */
    private void parseTypeDefList( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_TYPE );
        tokens.beginRule( "Type Definition List", Token.KW_TYPE ); /* cannot fail */
        tokens.match( Token.KW_TYPE );
        do {
            parseTypeDef( recoverSet.union( Token.IDENTIFIER ) );
        } while( tokens.isMatch( Token.IDENTIFIER ) );
        tokens.endRule( "Type Definition List", recoverSet );
    }
    /** Rule: TypeDef -> IDENTIFIER EQUALS Type SEMICOLON */
    private void parseTypeDef( TokenSet recoverSet ) {
        if( !tokens.beginRule("Type Definition", Token.IDENTIFIER, recoverSet ) ) {
            return;
        }
        assert tokens.isMatch( Token.IDENTIFIER );
        String name = tokens.getName();
        Position posn = tokens.getPosn();
        tokens.match( Token.IDENTIFIER );        /* cannot fail */
        tokens.match( Token.EQUALS, TYPE_START_SET );
        Type type = parseType( recoverSet.union( Token.SEMICOLON ) );
        if( symtab.getCurrentScope().addType( name, posn, type) == null ){
            errors.error( "Type identifier " + name + 
                   " already declared in this scope", posn );
        }
        tokens.match( Token.SEMICOLON, recoverSet );
        tokens.endRule( "Type Definition", recoverSet );
    }
    /** Rule: Type -> TypeIdentifier | SubrangeType */
    private Type parseType( TokenSet recoverSet ) {        
        if( ! tokens.beginRule( "Type", TYPE_START_SET, recoverSet ) ) {
            return Type.ERROR_TYPE;
        }
        assert tokens.isIn( TYPE_START_SET );
        Type type = null;
        if( tokens.isMatch( Token.IDENTIFIER ) ) {
            type = parseTypeIdentifier( recoverSet );
        } else if( tokens.isMatch( Token.LBRACKET ) ) {
            type = parseSubrangeType( recoverSet );
        } else {
            errors.fatal( "parseType", tokens.getPosn() );
        }
        tokens.endRule( "Type", recoverSet );
        return type;
    }
    /** Rule: SubrangeType -> LBRACKET Constant RANGE Constant RBRACKET */
    private Type parseSubrangeType( TokenSet recoverSet ) {        
        if( ! tokens.beginRule( "Subrange Type", Token.LBRACKET, recoverSet ) ) {
            return Type.ERROR_TYPE;
        }
        assert tokens.isMatch( Token.LBRACKET );
        tokens.match( Token.LBRACKET ); /* cannot fail */
        ConstExp lower, upper;
        lower = parseConstant( recoverSet.union( Token.RANGE ) );
        tokens.match( Token.RANGE, CONSTANT_START_SET );
        upper = parseConstant( recoverSet.union( Token.RBRACKET ) );
        tokens.match( Token.RBRACKET, recoverSet );
        tokens.endRule( "Subrange Type", recoverSet );
        return new Type.SubrangeType( lower, upper );
    }
    /** Rule: TypeIdentifier -> IDENTIFIER */
    private Type parseTypeIdentifier( TokenSet recoverSet ) {
        if( !tokens.beginRule("Type Identifier", Token.IDENTIFIER, recoverSet) ) {
            return Type.ERROR_TYPE;
        }
        assert tokens.isMatch( Token.IDENTIFIER );
        String name = tokens.getName();
        Position posn = tokens.getPosn();
        tokens.match( Token.IDENTIFIER );    /* cannot fail */
        tokens.endRule( "Type Identifier", recoverSet );
        return new Type.IdRefType( name, symtab.getCurrentScope(), posn );
    }
    /** Rule: VarDeclList -> KW_VAR VarDecl { VarDecl }  */
    private void parseVarDeclList( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_VAR );
        tokens.beginRule( "Variable Declaration List", Token.KW_VAR ); // cannot fail
        tokens.match( Token.KW_VAR ); /* cannot fail */
        do {
            parseVarDecl( recoverSet.union( Token.IDENTIFIER ) );
        } while( tokens.isMatch( Token.IDENTIFIER ) ); 
        tokens.endRule( "Variable Declaration List", recoverSet );
    }
    /** Rule: VarDecl -> IDENTIFIER COLON TypeIdentifier SEMICOLON */
    private void parseVarDecl( TokenSet recoverSet ) {
        if(!tokens.beginRule("Variable Declaration", Token.IDENTIFIER, recoverSet)) {
            return;
        }
        assert tokens.isMatch( Token.IDENTIFIER );
        String name = tokens.getName();
        Position posn = tokens.getPosn();
        tokens.match( Token.IDENTIFIER );     /* cannot fail */
        tokens.match( Token.COLON, TYPE_START_SET );
        Type type = parseTypeIdentifier( recoverSet.union( Token.SEMICOLON ) );
        // The type of a variable must be a reference type
        if( symtab.getCurrentScope().addVariable( name, posn, 
                new Type.ReferenceType(type) ) == null ) {
            errors.error( "Variable identifier " + name + 
                   " already declared in this scope", posn );
        }
        tokens.match( Token.SEMICOLON, recoverSet );
        tokens.endRule( "Variable Declaration", recoverSet );
    }
    /** Rule: ProcedureDef -> ProcedureHead EQUALS Block SEMICOLON */
    private DeclNode.ProcedureNode parseProcedureDef( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_PROCEDURE );
        tokens.beginRule( "Procedure Definition", Token.KW_PROCEDURE ); // can't fail
        /* A common syntax error is to forget the EQUALS, hence the 
         * recovery set contains tokens that can follow the EQUALS as well.
         * In general the recovery set can include tokens appearing later in
         * the production than immediately following tokens.
         */
        SymEntry.ProcedureEntry procEntry = parseProcedureHead( 
                recoverSet.union( Token.EQUALS ).union( BLOCK_START_SET ) );
        Scope blockLocals = symtab.newScope( procEntry );
        procEntry.setLocalScope( blockLocals );
        tokens.match( Token.EQUALS, BLOCK_START_SET );
        StatementNode.BlockNode block = 
                parseBlock(recoverSet.union(Token.SEMICOLON));
        block.setBlockLocals( blockLocals );
        symtab.leaveScope();
        tokens.match( Token.SEMICOLON, recoverSet );
        tokens.endRule( "Procedure Definition", recoverSet );
        return new DeclNode.ProcedureNode( procEntry, block );
    }
    /** Rule: ProcedureHead -> KW_PROCEDURE IDENTIFIER LPAREN RPAREN */
    private SymEntry.ProcedureEntry parseProcedureHead(TokenSet recoverSet) {
        assert tokens.isMatch( Token.KW_PROCEDURE );
        tokens.beginRule( "Procedure Header", Token.KW_PROCEDURE ); /* can't fail */
        SymEntry.ProcedureEntry procEntry;
        tokens.match( Token.KW_PROCEDURE );
        if( tokens.isMatch( Token.IDENTIFIER ) ) {
            procEntry = symtab.getCurrentScope().addProcedure(tokens.getName(),
                    tokens.getPosn());
            if( procEntry  == null ) {
                procEntry = new SymEntry.ProcedureEntry( tokens.getName(), 
                        tokens.getPosn() );
                procEntry.setScope(symtab.getCurrentScope());
                errors.error( "Procedure identifier " + tokens.getName() +
                       " already declared in this scope", tokens.getPosn()  );
            }
        } else {
            /* Provide dummy procedure entry (not in the symbol table) */
            procEntry = new SymEntry.ProcedureEntry( "<undefined>", 
                    tokens.getPosn() );
            procEntry.setScope(symtab.getCurrentScope());
        }
        tokens.match( Token.IDENTIFIER, Token.LPAREN );
        tokens.match( Token.LPAREN, Token.RPAREN );
        // Empty formal parameter list currently
        tokens.match( Token.RPAREN, recoverSet );
        tokens.endRule( "Procedure Header", recoverSet );        
        return procEntry;
    }
    /** Rule: CompoundStatement -> BEGIN StatementList END  */
    private StatementNode parseCompoundStatement( TokenSet recoverSet ) {
        if( !tokens.beginRule("Compound Statement", Token.KW_BEGIN, recoverSet) ) {
            return new StatementNode.ErrorNode( tokens.getPosn() );
        }
        assert tokens.isMatch( Token.KW_BEGIN );
        tokens.match( Token.KW_BEGIN );
        StatementNode result = 
            parseStatementList( recoverSet.union( Token.KW_END ) );
        tokens.match( Token.KW_END, recoverSet );
        tokens.endRule( "Compound Statement", recoverSet );
        return result;
    }
    /** Rule: StatementList -> Statement { SEMICOLON Statement }  */
    private StatementNode parseStatementList( TokenSet recoverSet ) {
        // Initialize result to an empty list of statements
        StatementNode.ListNode result = 
                new StatementNode.ListNode( tokens.getPosn() );
        if( !tokens.beginRule("Statement List",STATEMENT_START_SET,recoverSet) ) {
            return result;
        }
        assert tokens.isIn( STATEMENT_START_SET );
        StatementNode s = 
            parseStatement( recoverSet.union( Token.SEMICOLON ) );
        result.addStatement( s );        
        while( tokens.isMatch( Token.SEMICOLON ) ) {
            tokens.match( Token.SEMICOLON );
            s = parseStatement( recoverSet.union( Token.SEMICOLON ) );
            result.addStatement( s );
        }
        tokens.endRule( "Statement List", recoverSet );
        return result;
    }
    /** Rule: Statement -> Assignment | WhileStatement | IfStatement
     *                  | ReadStatement | WriteStatement | CallStatement
     *                  | CompoundStatement | SkipStatement | DoStatement
     */
    private StatementNode parseStatement( TokenSet recoverSet ) {
        StatementNode result;
        if ( !tokens.beginRule( "Statement", STATEMENT_START_SET, recoverSet ) ) {
            return new StatementNode.ErrorNode( tokens.getPosn() );
        }
        switch( tokens.getKind() ) {
        case IDENTIFIER:
            result = parseAssignment( recoverSet ); 
            break;
        case KW_WHILE:
            result = parseWhileStatement( recoverSet ); 
            break;
        case KW_IF:
            result = parseIfStatement( recoverSet );
            break;
        case KW_READ:
            result = parseReadStatement( recoverSet ); 
            break;
        case KW_WRITE:
            result = parseWriteStatement( recoverSet ); 
            break;
        case KW_CALL:
            result = parseCallStatement( recoverSet ); 
            break;
        case KW_BEGIN:
            result = parseCompoundStatement( recoverSet ); 
            break;
        case KW_SKIP:
            result = parseSkipStatement( recoverSet );
            break;
        case KW_DO:
            result = parseDoStatement( recoverSet );
            break;
        default:
            fatal( "parse Statement " );
            result = new StatementNode.ErrorNode( tokens.getPosn() );
        }
        tokens.endRule( "Statement", recoverSet );
        return result;
    }
    /** Rule: Assignment -> LValueList ASSIGN ConditionList */
    private StatementNode.AssignmentNode parseAssignment(TokenSet recoverSet) {
        if( !tokens.beginRule( "Assignment", LVALUE_START_SET, recoverSet ) ) {
            ArrayList<ExpNode> el = new ArrayList<ExpNode>();
            ArrayList<ExpNode> er = new ArrayList<ExpNode>();
            el.add( new ExpNode.ErrorNode( tokens.getPosn() ) );
            er.add( new ExpNode.ErrorNode( tokens.getPosn() ) );
            return new StatementNode.AssignmentNode( tokens.getPosn(), el, er );
        }
        /* Non-standard recovery set includes EQUALS because a common syntax
         * error is to use EQUALS instead of ASSIGN.
         */
        ArrayList<ExpNode> left = new ArrayList<ExpNode>();
        ArrayList<ExpNode> right = new ArrayList<ExpNode>();
        left.add( parseLValue( recoverSet.union( Token.ASSIGN, Token.EQUALS, Token.COMMA ) ) );
        Position pos = tokens.getPosn();
        while( tokens.isMatch( Token.COMMA ) ) {
            tokens.match( Token.COMMA, LVALUE_START_SET );
            left.add( parseLValue( recoverSet.union( Token.ASSIGN, Token.EQUALS, Token.COMMA ) ) );
            pos = tokens.getPosn();
        }
        tokens.match( Token.ASSIGN, CONDITION_START_SET );
        right.add( parseCondition( recoverSet.union( Token.COMMA ) ) );
        while( tokens.isMatch( Token.COMMA ) ) {
            tokens.match( Token.COMMA, CONDITION_START_SET );
            right.add( parseCondition( recoverSet.union( Token.COMMA ) ) );
        }
        /* At this point left and right could be different sizes. 
         * If left != right , we want to add in error nodes to balance it out.
         */
        if( left.size() > right.size() ) {
            errors.error("number of variables doesn't match number of expressions in assignment", pos);
            while( left.size() > right.size() ) {
                right.add( new ExpNode.ErrorNode( tokens.getPosn() ) );
            }
        } else if( left.size() < right.size() ) {
            errors.error("number of variables doesn't match number of expressions in assignment", pos);
            while( left.size() < right.size() ) {
                left.add( new ExpNode.ErrorNode( tokens.getPosn() ) );
            }
        }
        tokens.endRule( "Assignment", recoverSet );
        return new StatementNode.AssignmentNode( pos, left, right );
    }
    /** Rule: WhileStatement -> KW_WHILE Condition KW_DO Statement */
    private StatementNode parseWhileStatement( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_WHILE );
        tokens.beginRule( "While Statement", Token.KW_WHILE ); // cannot fail
        Position pos = tokens.getPosn();
        tokens.match( Token.KW_WHILE ); /* cannot fail */
        ExpNode cond = parseCondition( recoverSet.union( Token.KW_DO ) );
        tokens.match( Token.KW_DO, STATEMENT_START_SET );
        StatementNode statement = parseStatement( recoverSet );
        tokens.endRule( "While Statement", recoverSet );
        return new StatementNode.WhileNode( pos, cond, statement );
    }
    /** Rule: DoStatement -> KW_DO DoBranch { SEPARATOR DoBranch } KW_OD */
    private StatementNode parseDoStatement( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_DO );
        tokens.beginRule( "Do Statement", Token.KW_DO );    
        ArrayList<StatementNode.DoBranchNode> doBranches = new ArrayList<StatementNode.DoBranchNode>();
        Position pos = tokens.getPosn();
        tokens.match( Token.KW_DO );
        doBranches.add( parseDoBranch( recoverSet.union( Token.SEPARATOR, Token.KW_OD) ) );
        while( tokens.isMatch( Token.SEPARATOR ) ) {
            tokens.match( Token.SEPARATOR );
            doBranches.add( parseDoBranch( recoverSet.union( Token.SEPARATOR, Token.KW_OD) ) );
        }
        tokens.match( Token.KW_OD, recoverSet );
        tokens.endRule( "Do Statement", recoverSet);
        return new StatementNode.DoNode( pos, doBranches );
    }
    
    /** Rule: DoBranch -> Condition KW_THEN StatementList [KW_EXIT] */
    private StatementNode.DoBranchNode parseDoBranch( TokenSet recoverSet ) {
        tokens.beginRule( "Do Branch", CONDITION_START_SET );
        Position pos = tokens.getPosn();    
        ExpNode cond = parseCondition( recoverSet.union( Token.KW_THEN ) );
        tokens.match( Token.KW_THEN, STATEMENT_START_SET );
        StatementNode.ListNode statList = (StatementNode.ListNode) 
                parseStatementList( recoverSet.union( Token.KW_EXIT ) );
        boolean exits = false;  
        if( tokens.isMatch( Token.KW_EXIT ) ) {
            tokens.match( Token.KW_EXIT );
            exits = true;
        }   
        tokens.endRule( "Do Branch", recoverSet );
        return new StatementNode.DoBranchNode( pos, cond, statList, exits );
    }
    
    /** Rule: IfStatement -> KW_IF Condition KW_THEN Statement KW_ELSE Statement
     */
    private StatementNode parseIfStatement( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_IF );
        tokens.beginRule( "If Statement", Token.KW_IF ); /* cannot fail */
        tokens.match( Token.KW_IF ); /* cannot fail */
        Position pos = tokens.getPosn();
        ExpNode cond = parseCondition( recoverSet.union( Token.KW_THEN ) );
        tokens.match( Token.KW_THEN, STATEMENT_START_SET );
        StatementNode thenClause = 
            parseStatement( recoverSet.union( Token.KW_ELSE ) );
        tokens.match( Token.KW_ELSE, STATEMENT_START_SET );
        StatementNode elseClause = parseStatement( recoverSet );
        tokens.endRule( "If Statement", recoverSet );
        return new StatementNode.IfNode( pos, cond, thenClause, elseClause );
    }
    /** Rule: ReadStatement -> KW_READ LValue */
    private StatementNode parseReadStatement( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_READ );
        tokens.beginRule( "Read Statement", Token.KW_READ ); /* cannot fail */
        tokens.match( Token.KW_READ ); /* cannot fail */
        Position pos = tokens.getPosn();
        ExpNode lval = parseLValue( recoverSet );
        tokens.endRule( "Read Statement", recoverSet );
        // A read statement is treated as an assignment of the value read
        // to the variable. A ReadNode is an expression.
        ArrayList<ExpNode> left = new ArrayList<ExpNode>();
        ArrayList<ExpNode> right = new ArrayList<ExpNode>();
        left.add( lval );
        right.add( new ExpNode.ReadNode( pos ) );
        return new StatementNode.AssignmentNode( pos, left, right );
    }
    /** Rule: WriteStatement -> KW_WRITE Exp */
    private StatementNode parseWriteStatement( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_WRITE );
        tokens.beginRule( "Write Statement", Token.KW_WRITE ); // cannot fail
        tokens.match( Token.KW_WRITE ); /* cannot fail */
        Position pos = tokens.getPosn();
        ExpNode exp = parseExp( recoverSet );
        tokens.endRule( "Write Statement", recoverSet );
        return new StatementNode.WriteNode( pos, exp );
    }
    /** Rule: CallStatement -> KW_CALL IDENTIFIER LPAREN RPAREN */
    private StatementNode parseCallStatement( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_CALL );
        tokens.beginRule( "Call Statement", Token.KW_CALL ); // cannot fail
        tokens.match( Token.KW_CALL ); /* cannot fail */
        Position pos = tokens.getPosn();
        String procId;
        if( tokens.isMatch( Token.IDENTIFIER ) ) {
            procId = tokens.getName();
        } else {
            procId = "<noid>";
        }
        tokens.match( Token.IDENTIFIER, Token.LPAREN );
        tokens.match( Token.LPAREN, Token.RPAREN );
        // Empty actual parameter list currently
        tokens.match( Token.RPAREN, recoverSet );
        tokens.endRule( "Call Statement", recoverSet );
        return new StatementNode.CallNode( pos, procId 
                );
    }
    /** Rule: SkipStatement -> KW_SKIP */
    private StatementNode parseSkipStatement( TokenSet recoverSet ) {
        assert tokens.isMatch( Token.KW_SKIP );
        tokens.beginRule( "Skip Statement", Token.KW_SKIP);
        tokens.match(Token.KW_SKIP);
        Position pos = tokens.getPosn();
        return new StatementNode.SkipNode(pos);
    }
    /** Rule: Condition -> RelCondition */
    private ExpNode parseCondition( TokenSet recoverSet ) {
        return parseRelCondition( recoverSet );
    }
    /** Rule: RelCondition -> Exp [ RelOp Exp ] */
    private ExpNode parseRelCondition( TokenSet recoverSet ) {
        if( !tokens.beginRule( "Condition", CONDITION_START_SET, recoverSet ) ) {
            return new ExpNode.ErrorNode( tokens.getPosn() );
        }
        assert tokens.isIn( CONDITION_START_SET );
        ExpNode cond = parseExp( recoverSet.union( REL_OPS_SET ) );
        if( tokens.isIn( REL_OPS_SET ) ) {
            Position pos = tokens.getPosn();
            Operator operatorCode = 
                parseRelOp( recoverSet.union( EXP_START_SET ) );
            ExpNode right = parseExp( recoverSet );
            cond = new ExpNode.OperatorNode( pos, operatorCode, 
                    new ExpNode.ArgumentsNode( cond, right ) );
        }
        tokens.endRule( "Condition", recoverSet );
        return cond;
    }
    /** Rule: RelOp -> EQUALS | NEQUALS | LEQUALS | LESS | GREATER | GEQUALS */
    private Operator parseRelOp( TokenSet recoverSet ) {
        assert tokens.isIn( REL_OPS_SET );
        tokens.beginRule( "RelOp", REL_OPS_SET ); // cannot fail
        Operator operatorCode = Operator.INVALID_OP;
        switch( tokens.getKind() ) {
        case EQUALS:
            operatorCode = Operator.EQUALS_OP;
            tokens.match( Token.EQUALS ); /* cannot fail */
            break;
        case NEQUALS:
            operatorCode = Operator.NEQUALS_OP;
            tokens.match( Token.NEQUALS ); /* cannot fail */
            break;
        case LESS:
            operatorCode = Operator.LESS_OP;
            tokens.match( Token.LESS ); /* cannot fail */
            break;
        case GREATER:
            operatorCode = Operator.GREATER_OP; 
            tokens.match( Token.GREATER ); /* cannot fail */
            break;
        case LEQUALS:
            operatorCode = Operator.LEQUALS_OP;
            tokens.match( Token.LEQUALS ); /* cannot fail */
            break;
        case GEQUALS:
            operatorCode = Operator.GEQUALS_OP;
            tokens.match( Token.GEQUALS ); /* cannot fail */
            break;
        default:
            fatal( "Unreachable branch in parseCondition" );
        }
        tokens.endRule( "RelOp", recoverSet );
        return operatorCode;
    }
    /** Rule: Exp -> [ PLUS | MINUS ] Term { ( PLUS | MINUS ) Term } */
    private ExpNode parseExp( TokenSet recoverSet ) {
        if( !tokens.beginRule( "Expression", EXP_START_SET, recoverSet ) ) {
            return new ExpNode.ErrorNode( tokens.getPosn() );
        }
        assert tokens.isIn( EXP_START_SET );
        boolean haveUnaryMinus = false;
        Position pos = tokens.getPosn();
        if( tokens.isMatch( Token.MINUS ) ) {
            haveUnaryMinus = true;
            tokens.match( Token.MINUS ); /* cannot fail */
        } else if( tokens.isMatch( Token.PLUS ) ) {
            tokens.match( Token.PLUS ); /* cannot fail */
        }
        ExpNode exp = parseTerm( recoverSet.union( EXP_OPS_SET ) );
        if( haveUnaryMinus ) {
            exp = new ExpNode.OperatorNode( pos, Operator.NEG_OP, exp );
        }
        while( tokens.isIn( EXP_OPS_SET ) ) {
            Operator operatorCode = Operator.INVALID_OP;
            pos = tokens.getPosn();
            if ( tokens.isMatch( Token.MINUS ) ) {
                operatorCode = Operator.SUB_OP;
                tokens.match( Token.MINUS ); /* cannot fail */
            } else if ( tokens.isMatch( Token.PLUS ) ) {
                operatorCode = Operator.ADD_OP;
                tokens.match( Token.PLUS ); /* cannot fail */
            } else {
                fatal( "Unreachable branch in parseExp" );
            }
            ExpNode right = parseTerm( recoverSet.union( EXP_OPS_SET ) );
            exp = new ExpNode.OperatorNode( pos, operatorCode, 
                    new ExpNode.ArgumentsNode( exp, right ) );
        }
        tokens.endRule( "Expression", recoverSet );
        return exp;
    }
    /** Rule: Term  -> Factor { ( TIMES | DIVIDE ) Factor }  */
    private ExpNode parseTerm( TokenSet recoverSet ) {
        if( !tokens.beginRule( "Term", TERM_START_SET, recoverSet ) ) {
            return new  ExpNode.ErrorNode( tokens.getPosn() );
        }
        assert tokens.isIn( TERM_START_SET );
        ExpNode term = parseFactor( recoverSet.union( TERM_OPS_SET ) );
        while( tokens.isIn( TERM_OPS_SET ) ) {
            Operator operatorCode = Operator.INVALID_OP;
            Position pos = tokens.getPosn();
            if ( tokens.isMatch( Token.TIMES ) ) {
                operatorCode = Operator.MUL_OP;
                tokens.match( Token.TIMES ); /* cannot fail */
            } else if ( tokens.isMatch( Token.DIVIDE ) ) {
                operatorCode = Operator.DIV_OP;
                tokens.match( Token.DIVIDE ); /* cannot fail */
            } else {
                fatal( "Unreachable branch in parseTerm" );
            }
            ExpNode right = parseFactor( recoverSet.union( TERM_OPS_SET ) );
            term = new ExpNode.OperatorNode( pos, operatorCode, 
                    new ExpNode.ArgumentsNode( term, right ) );
        }
        tokens.endRule( "Term", recoverSet );
        return term;
    }
    /** Rule: Factor -> LPAREN Condition RPAREN | NUMBER | LValue  */
    private ExpNode parseFactor( TokenSet recoverSet ) {
        if( !tokens.beginRule( "Factor", FACTOR_START_SET, recoverSet ) ) {
            return new ExpNode.ErrorNode( tokens.getPosn() );
        }
        assert tokens.isIn( FACTOR_START_SET );
        ExpNode result = null;
        if( tokens.isMatch( Token.IDENTIFIER ) ) {
            result = parseLValue( recoverSet );
        } else if( tokens.isMatch( Token.NUMBER ) ) {
            result = new ExpNode.ConstNode( tokens.getPosn(), 
                    Predefined.INTEGER_TYPE, tokens.getIntValue() );
            tokens.match( Token.NUMBER ); /* cannot fail */
        } else if( tokens.isMatch( Token.LPAREN ) ) {
            tokens.match( Token.LPAREN ); /* cannot fail */
            result = parseCondition( recoverSet.union( Token.RPAREN ) );
            tokens.match( Token.RPAREN, recoverSet );
        } else {
            fatal( "Unreachable branch in Factor" );
        }
        tokens.endRule( "Factor", recoverSet );
        return result;
    }
    /** Rule: LValue -> IDENTIFIER */
    private ExpNode parseLValue( TokenSet recoverSet ) {
        if( !tokens.beginRule( "LValue", Token.IDENTIFIER, recoverSet ) ) {
            return new ExpNode.ErrorNode( tokens.getPosn() );
        }
        assert tokens.isMatch( Token.IDENTIFIER );
        ExpNode result = 
            new ExpNode.IdentifierNode( tokens.getPosn(), tokens.getName() );
        tokens.match( Token.IDENTIFIER ); /* cannot fail */
        tokens.endRule( "LValue", recoverSet );
        return result;
    }

/*********************** Private convenience Methods ************************/
    /** Signal a fatal error at the current token position */
    private void fatal( String m ) {
        errors.fatal( m, tokens.getPosn() );
    }
}
