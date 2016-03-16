package parser;

import source.ErrorHandler;
import source.Errors;
import source.Position;
import source.Source;

/**
 * LexicalToken - Defines the basic tokens returned from the lexical analyser.
 * @version $Revision: 22 $  $Date: 2014-05-20 15:14:36 +1000 (Tue, 20 May 2014) $
 *   Arguably should be an inner class of Scanner, but is a little
 *   easier this way. Also gets heavily used by Parser.
 */

public class LexicalToken {
    
    /** Kind of token, e.g. IDENTIFIER, NUMBER, PLUS */
    private Token kind;
    /** The position of the first char of the token in the input source */
    private Position posn; 
    /** Input source */
    private static Source source;
    /** Error handler */
    private static Errors errors = ErrorHandler.getErrorHandler();

/****************** Constructors ********************/

    /** Construct a token with the given type and position. 
     * @param kind Type of the lexical token
     * @param posn Position in the source input file
     */
    public LexicalToken( Token kind, Position posn ) {
        this.kind = kind;
        this.posn = posn;
    }

/****************** Public Methods ******************/
    
    /** Set the source input file for tokens */
    public static void setSource( Source src ) {
        source = src;
    }

    /** Extract the type of a token */
    public Token getKind( ) {
        return kind;
    }

    /** Extract the position of a token */
    public Position getPosn( ) {
        return posn;
    }
    
    /** Test if the type of the token matches the argument 
     * @param expected Type to be matched
     */ 
    public boolean isMatch( Token kind ) {
        return this.kind == kind;
    }
    /** Don't want to accidentally use equals instead of isMatch */
    @Override
    public boolean equals( Object o ) {
        errors.fatal( "Use isMatch to compare token kind", posn );
        return false;
    }
    
    /** Test if the token is contained in the given set of token types
     * @param tokenTypes set of tokens to test against
     * @return true iff in the set
     */
    public boolean isIn( TokenSet tokenTypes ) {
        return tokenTypes.contains( this.kind );
    }

    /* Virtual extract integer value of INTEGER token */
    public int getIntValue( ) {
        errors.fatal("call on getIntValue on a Token", posn );
        return 0;
    }

    /* Virtual extract name of IDENTIFIER token */
    public String getName( ) {
        errors.fatal("Internal error: call on getName on a Token", posn);
        return null;
    }
    
    /** Return token name and position as debug string */
    public String toString() {
        return "'" + kind.toString() + "'" + 
            " at line " + source.getLineNumber( getPosn() ) +
            " column " + source.offset( getPosn() );
    }
}
