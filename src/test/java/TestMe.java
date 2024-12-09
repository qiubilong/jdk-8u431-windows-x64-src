public class TestMe {

    static final int SHARED_SHIFT   = 16;
    static final int SHARED_UNIT    = (1 << SHARED_SHIFT);  /* 2^16 */

    public static void main(String[] args) {
        System.out.println(SHARED_UNIT);
    }
}
