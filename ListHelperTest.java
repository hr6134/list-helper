import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author hr6134
 */
public class ListHelperTest {
    public static void main(String[] args) {
        List<LHTest> list = new ArrayList<>();
        list.add(new LHTest(3, "ab", new Dummy("c"), 15));
        list.add(new LHTest(2, "bc", new Dummy("b"), 25));
        list.add(new LHTest(1, "ab", new Dummy("a"), 14));
        list.add(new LHTest(5, "de", new Dummy("e"), 45));
        list.add(new LHTest(4, "ab", new Dummy("d"), 42));

        List<String> result1 = new ListHelper<>(list, LHTest.class).get("name", String.class)
                .like("c").slice(0, null).orderAsc().values();
        System.out.println(result1);

        List<Integer> result2 = new ListHelper<>(list, LHTest.class).get("id", Integer.class)
                .gt(2).le(5).orderDesc().values();
        System.out.println(result2);

        List<Dummy> result3 = new ListHelper<>(list, LHTest.class).get("dummy", Dummy.class).slice(1, 4).values();
        for (Dummy i : result3) {
            System.out.println(i.value);
        }

        ListHelper.Lambda<Integer> sum = new ListHelper.Lambda<Integer>() {
            @Override
            public Integer lambda(List<Integer> list) {
                Integer tmp = 0;
                for (Integer i : list) {
                    tmp += i;
                }
                return tmp;
            }
        };
        List<Integer> result4 = new ListHelper<>(list, LHTest.class).groupInList("name", String.class,
                "value", Integer.class, sum).values();
        System.out.println(result4);

        Map<String, Integer> result5 = new ListHelper<>(list, LHTest.class).groupInMap("name", String.class,
                "value", Integer.class, sum);
        System.out.println(result5);
    }
}

class Dummy {
    String value;

    Dummy(String value) {
        this.value = value;
    }
}

class LHTest {
    private Integer id;
    private String name;
    private Dummy dummy;
    private Integer value;

    LHTest(Integer id, String name, Dummy dummy) {
        this.id = id;
        this.name = name;
        this.dummy = dummy;
    }

    LHTest(Integer id, String name, Dummy dummy, Integer value) {
        this.id = id;
        this.name = name;
        this.dummy = dummy;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LHTest lhTest = (LHTest) o;

        if (dummy != null ? !dummy.equals(lhTest.dummy) : lhTest.dummy != null) return false;
        if (id != null ? !id.equals(lhTest.id) : lhTest.id != null) return false;
        if (name != null ? !name.equals(lhTest.name) : lhTest.name != null) return false;
        if (value != null ? !value.equals(lhTest.value) : lhTest.value != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (dummy != null ? dummy.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
