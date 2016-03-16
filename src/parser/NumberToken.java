package parser;

import source.Position;

/**
 * class NumberToken - Number token needs an integer value
 * @version $Revision: 14 $  $Date: 2013-05-08 10:40:38 +1000 (Wed, 08 May 2013) $
 */ 
public class NumberToken extends LexicalToken {

    private int intValue;

    /** Construct a token with the given type, position and integer value. 
     * @param type should always be NUMBER.
     */
    public NumberToken( Token type, Position posn, int intValue ) {
        super(type,posn);
        this.intValue = intValue;
    }
    /** Extract integer value of NUMBER token */
    @Override
    public int getIntValue( ) {
        return intValue;
    }
    @Override
    public String toString() {
        return "number(" + intValue + ")";
    }
}
