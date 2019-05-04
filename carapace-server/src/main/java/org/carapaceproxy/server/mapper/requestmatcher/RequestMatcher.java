package org.carapaceproxy.server.mapper.requestmatcher;

import io.netty.handler.codec.http.HttpRequest;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Pattern;
import org.carapaceproxy.server.RequestHandler;
import org.eclipse.jetty.http.HttpHeader;

public class RequestMatcher implements RequestMatcherConstants {

    private static boolean jj_initialized_once = false;
    /**
     * Generated Token Manager.
     */
    private static RequestMatcherTokenManager token_source;
    private static SimpleCharStream jj_input_stream;
    /**
     * Current token.
     */
    private static Token token;
    /**
     * Next token.
     */
    private static Token jj_nt;
    private static int jj_ntk;
    private static int jj_gen;
    private static final int[] jj_la1 = new int[6];
    private static int[] jj_la1_0;

    static {
        jj_la1_init_0();
    }

    private static HttpRequest request;

    public static boolean matches(HttpRequest _request, String matchingCondition) throws ParseException, IOException {
        try (Reader reader = new StringReader(matchingCondition)) {
            init(reader);
            request = _request;
            String regexp;
            boolean cond;
            boolean expr;

            switch ((jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {
                case ALL: {
                    jj_consume_token(ALL);
                    cond = true;
                    break;
                }
                case NOT:
                case HTTPS:
                case O_BRACKET: {
                    cond = expression();
                    break;
                }
                case REGEXP_DEF: {
                    jj_consume_token(REGEXP_DEF);
                    regexp = jj_consume_token(REGEXP).image;
                    regexp = regexp.substring(1, regexp.length() - 1);
                    cond = Pattern.compile(regexp).matcher(request.uri()).matches();
                    switch ((jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {
                        case AND:
                        case OR: {
                            switch ((jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {
                                case AND: {
                                    jj_consume_token(AND);
                                    expr = expression();
                                    cond &= expr;
                                    break;
                                }
                                case OR: {
                                    jj_consume_token(OR);
                                    expr = expression();
                                    cond |= expr;
                                    break;
                                }
                                default:
                                    jj_la1[0] = jj_gen;
                                    jj_consume_token(-1);
                                    throw new ParseException();
                            }
                            break;
                        }
                        default:
                            jj_la1[1] = jj_gen;
                    }
                    break;
                }
                default:
                    jj_la1[2] = jj_gen;
                    jj_consume_token(-1);
                    throw new ParseException();
            }
            return cond;
        } finally {
            jj_initialized_once = false;
        }
    }

    private static void init(java.io.Reader stream) {
        if (jj_initialized_once) {
            System.out.println("ERROR: Second call to matches of static parser.");
            throw new Error();
        }
        jj_initialized_once = true;
        if (jj_input_stream == null) {
            jj_input_stream = new SimpleCharStream(stream, 1, 1);
            token_source = new RequestMatcherTokenManager(jj_input_stream);
        } else {
            jj_input_stream.ReInit(stream, 1, 1);
            token_source.ReInit(jj_input_stream);
        }
        token = new Token();
        jj_ntk = -1;
        jj_gen = 0;
        for (int i = 0; i < 6; i++) {
            jj_la1[i] = -1;
        }
    }

    private static boolean expression() throws ParseException {
        boolean term;
        term = term();
        label_1:
        while (true) {
            switch ((jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {
                case OR: {
                    break;
                }
                default:
                    jj_la1[3] = jj_gen;
                    break label_1;
            }
            jj_consume_token(OR);
            term |= term();
        }

        return term;
    }

    private static boolean term() throws ParseException {
        boolean factor;
        factor = factor();
        label_2:
        while (true) {
            switch ((jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {
                case AND: {
                    break;
                }
                default:
                    jj_la1[4] = jj_gen;
                    break label_2;
            }
            jj_consume_token(AND);
            factor &= factor();
        }

        return factor;
    }

    private static boolean factor() throws ParseException {
        boolean value;
        switch ((jj_ntk == -1) ? jj_ntk_f() : jj_ntk) {
            case HTTPS: {
                value = value();
                break;
            }
            case NOT: {
                jj_consume_token(NOT);
                value = factor();
                value = !value;
                break;
            }
            case O_BRACKET: {
                jj_consume_token(O_BRACKET);
                value = expression();
                jj_consume_token(C_BRACKET);
                break;
            }
            default:
                jj_la1[5] = jj_gen;
                jj_consume_token(-1);
                throw new ParseException();
        }
        return value;
    }

    private static boolean value() throws ParseException {
        jj_consume_token(HTTPS);
        {
            return RequestHandler.PROTO_HTTPS.equals(request.headers().get(HttpHeader.X_FORWARDED_PROTO.name(), ""));
        }
    }

    private static void jj_la1_init_0() {
        jj_la1_0 = new int[]{0x180, 0x180, 0xe60, 0x100, 0x80, 0xe00,};
    }

    static private Token jj_consume_token(int kind) throws ParseException {
        Token oldToken;
        if ((oldToken = token).next != null) {
            token = token.next;
        } else {
            token = token.next = token_source.getNextToken();
        }
        jj_ntk = -1;
        if (token.kind == kind) {
            jj_gen++;
            return token;
        }
        token = oldToken;
        jj_kind = kind;
        throw generateParseException();
    }

    /**
     * Get the next Token.
     */
    static final public Token getNextToken() {
        if (token.next != null) {
            token = token.next;
        } else {
            token = token.next = token_source.getNextToken();
        }
        jj_ntk = -1;
        jj_gen++;
        return token;
    }

    /**
     * Get the specific Token.
     */
    static final public Token getToken(int index) {
        Token t = token;
        for (int i = 0; i < index; i++) {
            if (t.next != null) {
                t = t.next;
            } else {
                t = t.next = token_source.getNextToken();
            }
        }
        return t;
    }

    static private int jj_ntk_f() {
        if ((jj_nt = token.next) == null) {
            return (jj_ntk = (token.next = token_source.getNextToken()).kind);
        } else {
            return (jj_ntk = jj_nt.kind);
        }
    }

    static private java.util.List<int[]> jj_expentries = new java.util.ArrayList<int[]>();
    static private int[] jj_expentry;
    static private int jj_kind = -1;

    /**
     * Generate ParseException.
     */
    static public ParseException generateParseException() {
        jj_expentries.clear();
        boolean[] la1tokens = new boolean[14];
        if (jj_kind >= 0) {
            la1tokens[jj_kind] = true;
            jj_kind = -1;
        }
        for (int i = 0; i < 6; i++) {
            if (jj_la1[i] == jj_gen) {
                for (int j = 0; j < 32; j++) {
                    if ((jj_la1_0[i] & (1 << j)) != 0) {
                        la1tokens[j] = true;
                    }
                }
            }
        }
        for (int i = 0; i < 14; i++) {
            if (la1tokens[i]) {
                jj_expentry = new int[1];
                jj_expentry[0] = i;
                jj_expentries.add(jj_expentry);
            }
        }
        int[][] exptokseq = new int[jj_expentries.size()][];
        for (int i = 0; i < jj_expentries.size(); i++) {
            exptokseq[i] = jj_expentries.get(i);
        }
        return new ParseException(token, exptokseq, tokenImage);
    }

    /**
     * Enable tracing.
     */
    static final public void enable_tracing() {
    }

    /**
     * Disable tracing.
     */
    static final public void disable_tracing() {
    }

}
