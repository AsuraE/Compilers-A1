package source;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * class ErrorHandler - Handles error message generated by the compiler.
 * @version $Revision: 14 $  $Date: 2013-05-08 10:40:38 +1000 (Wed, 08 May 2013) $
 */
public class ErrorHandler implements Errors 
{
    private static final int LINE_NUM_WIDTH = 6;
    private static final int MAX_ERRORS = 100;

    /** global handle on the singleton error handler. */
    private static ErrorHandler handler = null;

    /** Accumulated error messages. */
    private List<CompileError> errors;
    /** Number of errors. */
    private int numberOfErrors;
    /** Output stream to report errors to. */
    private PrintStream output;
    /** Input source file to print lines of source with error message. */
    private Source source;
    /** Used for accessing source file. */
    private BufferedReader inputStream;
    /** Index with source file. */
    private int inputIndex;
    /** Print debugging messages if true */
    private boolean debug;
    /** indent level for debugging messages */
    private int debugLevel;
    
    public ErrorHandler( PrintStream output, Source source, boolean debug ) {
        assert handler == null; // only one instance allowed
        this.errors = new ArrayList<CompileError>( MAX_ERRORS );
        this.numberOfErrors = 0;
        this.output = output;
        this.source = source;
        this.debug = debug;
        this.debugLevel = 0;
        handler = this;
    }
    /** count errors of each severity for the program. 
    private Map<Severity, Integer> errorCounts = 
        new EnumMap<Severity, Integer>( Severity.class ); 
    */

    /** @return the singleton error handler. */
    public static Errors getErrorHandler() {
        return handler;
    }
    /** Signal an error at the given position */
    public void error( String m, Position pos ) {
        errorMessage( m, Severity.ERROR, pos );
    }
    /** Signal a fatal error at the given position */
    public void fatal( String m, Position pos ) {
        errorMessage( m, Severity.FATAL, pos );
    }
    /** Output debugging message if debug turned on */
    public void debugMessage( String msg ) {
        if( debug ) {
            /* Indent message by the level of nesting of parsing rules */
            String indent = "";
            for( int i = 1; i <= debugLevel; i++ ) {
                indent += " ";
            }
            println( indent + msg );
        }
    }
    /** Increment debug level */
    public void incDebug() {
        debugLevel++;
    }
    /** Decrement debug level */
    public void decDebug() {
        debugLevel--;
    }
    /** Check that condition is true. Otherwise throw an error which should
     * abort the parser immediately
     */
    public void checkAssert( boolean condition, String m, Position pos ) {
        if( !condition ) {
            fatal( "Assertion failed! " + m, pos );
        }
    }
    /** Print out all pending messages and clear the queue */
    public void flush( ) {
        listMessages();
        errors.clear();
    }
    /** errorSummary reports the number of errors detected. */
    public void errorSummary() {
        if( numberOfErrors == 0 ) {
            output.println( "No errors detected." );
        } else if ( numberOfErrors == 1 ) {
            output.println( "1 error detected.");
        } else {
            output.println( numberOfErrors + " errors detected." );
        }
    }
    /** hadErrors   
     * @return true if an error has been recorded.  
     */
    public boolean hadErrors() {
        return numberOfErrors > 0; 
    }
    /** Print line to output stream */
    public void println( String msg ) {
        output.println( msg );
    }
    
    /** Add an error, up to the limit of MAX_ERRORS.
     * After that errors messages are discarded,
     * but a count of the total number of errors maintained.
     * A FATAL error causes the accumulated error messages
     * to be flushed and a Java Fatal Error to be thrown.
     */
    private void errorMessage( CompileError error ) {
        if( numberOfErrors < MAX_ERRORS ) {
            errors.add( error );
        }
        numberOfErrors++;
        if( error.getSeverity() == Severity.FATAL ) {
            listMessages();
            errorSummary();
            throw new Error( "Fatal error" );
        }
    }
    /** Add an error message, but with three separate arguments. */
    private void errorMessage(String message, Severity severity, Position pos ) {
        errorMessage( new CompileError( message, severity, pos ) );
    }
    /** List the messages reported in this line.  If an error
     * relates a source line the position is indicated by an arrow.
     */      
    private void listMessages() {
        try {
            inputStream = new BufferedReader( 
                    new FileReader( source.getFileName() ) );
        } catch (FileNotFoundException e1) {
            System.err.println( "ErrorHandler.listmessages: " +
                    source.getFileName() + " not found");
            System.exit(1);
        }
        inputIndex = 0;
        int previousLineNumber = -1;
        Collections.sort( errors );
        for( CompileError e : errors ) {
            int lineNumber = previousLineNumber;
            if( ! e.getPosition().equals( Position.NO_POSITION ) ) {
                lineNumber = source.getLineNumber( e.getPosition() );
                if( lineNumber != previousLineNumber ) {
                    printPaddedInteger( output, lineNumber, LINE_NUM_WIDTH );
                    output.print( ' ' );
                    printLine( e.getPosition() );
                }
                errorPad( output, LINE_NUM_WIDTH );
                output.print( ' ' );
                for( int i = 0; i < source.offset( e.getPosition() ); i++ ) {
                    output.print( ' ' );
                }
                output.print( "^ " );
            } else {
                errorPad( output, LINE_NUM_WIDTH );
                output.print( ' ' );
            }
            output.println( e.toString() );
            previousLineNumber = lineNumber;
        }
    }
    
    /** Print the line from source file.
     * @param position within source file - the line containing that 
     *        position is printed.
     * inputIndex is updated to keep track of the current position 
     * within the input stream of the source file.
     */
    private void printLine( Position position ) {
        try {
            int ch;
            int startOfLine = source.getLineStart( position ).getIndex();
            inputStream.skip( startOfLine - inputIndex );
            inputIndex = startOfLine;
            do {
                ch = inputStream.read();
                if( ch < 0 ) {
                    // If end-of-file reached before end-of-line
                    // output a new line
                    output.write( '\n' );
                    break;
                }
                output.write( ch );
                inputIndex++;
            } while( ch != '\n' );
        } catch( IOException e ) {
            System.err.println( "IOException printing error messages" );
        }
    }
    
    /** Print value in the number of columns given. */
    private void printPaddedInteger( PrintStream output, int val, int cols ) {
        String s = Integer.toString(val);
        for (int i = s.length(); i<cols; i++ ) {
            output.print(' ');
        }
        output.print( s );
    }
    
    /** Print asterisks to width of line number column. */
    private void errorPad( PrintStream output, int width ) {
        for( int i = 0; i < width; i++ ) {
            output.print( '*' );
        }
    }
}
