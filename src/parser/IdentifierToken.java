package parser;

import source.Position;

/**
 * class IdentifierToken - Identifier token needs an identifier name
 * @version $Revision: 14 $  $Date: 2013-05-08 10:40:38 +1000 (Wed, 08 May 2013) $
 */ 
public class IdentifierToken extends LexicalToken {

    private String name;

    /** Construct a token with the given type, position and string value. 
     * @param type should normally be IDENTIFIER.
     */
    public IdentifierToken( Token type, Position posn, String name ) {
        super(type,posn);
        this.name = name;
    }
    /** Extract name of IDENTIFIER token */
    @Override
    public String getName( ) {
        return name;
    }
    @Override
    public String toString() {
        return "identifier(\"" + name + "\")";
    }
}
