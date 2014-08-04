import java.lang.reflect.Field;
import java.util.*;

/**
 * Утилитный класс позволяющий работать со списками в стиле ORM.
 * Строить на их основе списки отдельных полей объектов, и применять к ним условия.
 * Например:
 * Есть класс LHTest содержащий поля id, name, dummy и value. И список объектов этого класса list.
 * а) получить список значений поля name:
 * new ListHelper<>(list, LHTest.class).get("name", String.class).values()
 * б) получить первые 10 имён содержащие в себе строку black и отсортировать их в порядке возрастания:
 * new ListHelper<>(list, LHTest.class).get("name", String.class).like("black").slice(0, 10).orderAsc().values();
 *
 * С остальными примерами можно ознакомиться в классе ListHelperTest.
 *
 * @author hr6134
 */
public class ListHelper<T> {
    private Collection<T> collection;
    private Class<? super T> type;

    public ListHelper(Collection<T> collection, Class<T> type) {
        if (collection == null) {
            throw new IllegalArgumentException("collection argument can't be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type argument can't be null");
        }

        this.collection = collection;
        this.type = type;
    }

    public ListHelper<T> put(Collection<T> collection, Class<T> type) {
        if (collection == null) {
            throw new IllegalArgumentException("collection argument can't be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type argument can't be null");
        }

        this.collection = collection;
        this.type = type;
        return this;
    }

    /**
     * Получает список нод со значениями указанного поля из оригинального списка.
     *
     * @param fieldName название поля, по которому будет строиться список.
     * @param clazz класс поля, по которому строиться список.
     * @return список нод
     */
    public <Q> ListHelperNodes<Q> get(String fieldName, Class<Q> clazz) {
        List<Q> nodes = new ArrayList<>();
        for (T item : collection) {
            try {
                Field field = null;
                while (true) {
                    try {
                        field = type.getDeclaredField(fieldName);
                        break;
                    } catch (NoSuchFieldException e) {
                        type = type.getSuperclass();
                        if (type == null) {
                            break;
                        }
                    }
                }
                if (field == null) {
                    return new ListHelperNodes<>(nodes, clazz);
                }

                field.setAccessible(true);
                Class fieldClass = (Class) field.getGenericType();
                if (fieldClass.equals(clazz)) {
                    Q obj = (Q) field.get(item);
                    nodes.add(obj);
                } else {
                    throw new ClassCastException("Field type and clazz argument must be consisted.");
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return new ListHelperNodes<>(nodes, clazz);
    }

    public <Q, F> ListHelperNodes<F> groupInList(String groupFieldName, Class<Q> groupFieldClazz,
                                                 String aggrFieldName, Class<F> aggrFieldClazz, Lambda<F> lambda) {
        Map<Q, F> map = groupInMap(groupFieldName, groupFieldClazz, aggrFieldName, aggrFieldClazz, lambda);

        List<F> nodes = new ArrayList<>();
        for (Map.Entry<Q, F> entry : map.entrySet()) {
            nodes.add(entry.getValue());
        }

        return new ListHelperNodes<>(nodes, aggrFieldClazz);
    }

    /**
     * Аггрегирует одно из полей по отношению к другому с помощью переданной функции.
     *
     * @param groupFieldName группирующее поле
     * @param groupFieldClazz класс группирующего поля
     * @param aggrFieldName аггрегируемое поле
     * @param aggrFieldClazz класс аггрегируемого поля
     * @param lambda аггрегатная функция
     * @return словарь, где ключи это группирующие поля, а значения это аггрегированные поля
     */
    public <Q, F> Map<Q, F> groupInMap(String groupFieldName, Class<Q> groupFieldClazz,
                                       String aggrFieldName, Class<F> aggrFieldClazz, Lambda<F> lambda) {
        Map<Q, List<F>> map = new HashMap<>();
        for (T item : collection) {
            try {
                Field field = null;
                while (true) {
                    try {
                        field = type.getDeclaredField(groupFieldName);
                        break;
                    } catch (NoSuchFieldException e) {
                        type = type.getSuperclass();
                        if (type == null) {
                            break;
                        }
                    }
                }

                Field aggrField = null;
                while (true) {
                    try {
                        aggrField = type.getDeclaredField(aggrFieldName);
                        break;
                    } catch (NoSuchFieldException e) {
                        type = type.getSuperclass();
                        if (type == null) {
                            break;
                        }
                    }
                }

                if (field == null || aggrField == null) {
                    return new HashMap<>();
                }

                field.setAccessible(true);
                Class fieldClass = (Class) field.getGenericType();
                aggrField.setAccessible(true);
                Class aggrFieldClass = (Class) aggrField.getGenericType();
                if (fieldClass.equals(groupFieldClazz) && aggrFieldClass.equals(aggrFieldClazz)) {
                    Q key = (Q) field.get(item);
                    final F value = (F) aggrField.get(item);

                    if (map.containsKey(key)) {
                        map.get(key).add(value);
                    } else {
                        map.put(key, new ArrayList<F>() {{ add(value); }});
                    }
                } else {
                    throw new ClassCastException("Field type and clazz argument must be consisted.");
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        Map<Q, F> nodes = new HashMap<>();
        for (Map.Entry<Q, List<F>> entry : map.entrySet()) {
            nodes.put(entry.getKey(), lambda.lambda(entry.getValue()));
        }

        return nodes;
    }

    /**
     * Вспомогательный класс, объекты которого составляют элементы результирующего списка.
     * Позволяет проводить дополнительные манипуляции со списком, как то:
     * а) делать срезы
     * б) сортировать список
     * в) выбирать только уникальные значения
     * г) сравнивать значения: больше, меньше etc
     * д) сравнивать текст с помощью оператора like
     * 
     * В случае, если значения не могуть быть подвергнуты фильтрации, фильтрация игнорируется.
     * Условия могут компоноваться в цепочку вызовов.
     */
    public class ListHelperNodes<E> {
        List<E> fields;
        Class clazz;

        private ListHelperNodes(List<E> fields, Class clazz) {
            if (clazz == null) {
                throw new IllegalArgumentException("clazz argument can't be null");
            }

            this.fields = fields;
            this.clazz = clazz;
        }

        public List<E> values() {
            return fields;
        }

        public ListHelperNodes<E> slice(Integer fromIndex, Integer toIndex) {
            if (fromIndex == null) {
                fromIndex = 0;
            }
            if (toIndex == null) {
                toIndex = fields.size();
            }
            if (fields.size() == 0 || fromIndex > toIndex) {
                return this;
            }
            while (fromIndex < 0) {
                fromIndex = fields.size() + fromIndex;
            }
            while (toIndex < 0) {
                toIndex = fields.size() + toIndex;
            }
            if (fromIndex > fields.size()) {
                fromIndex = fields.size();
            }
            if (toIndex > fields.size()) {
                toIndex = fields.size();
            }

            this.fields = fields.subList(fromIndex, toIndex);
            return this;
        }
        
        public ListHelperNodes<E> distinct() {
            List<E> tmp = new ArrayList<>();
            for (E e : fields) {
                if (!tmp.contains(e)) {
                    tmp.add(e);
                }
            }
            fields = tmp;
            return this;
        }

        public ListHelperNodes<E> order(OrderEnum orderEnum) {
            if (Arrays.asList(clazz.getInterfaces()).contains(Comparable.class)) {
                Collections.sort((List<Comparable>)fields);
                if (orderEnum == OrderEnum.DESC) {
                    Collections.reverse(fields);
                }
            }
            return this;
        }

        public ListHelperNodes<E> orderAsc() {
            order(OrderEnum.ASC);
            return this;
        }

        public ListHelperNodes<E> orderDesc() {
            order(OrderEnum.DESC);
            return this;
        }

        public ListHelperNodes<E> gt(E value) {
            if (Arrays.asList(clazz.getInterfaces()).contains(Comparable.class)) {
                Iterator iterator = fields.iterator();
                while (iterator.hasNext()) {
                    if (((Comparable)iterator.next()).compareTo(value) <= 0) {
                        iterator.remove();
                    }
                }
            }
            return this;
        }

        public ListHelperNodes<E> ge(E value) {
            if (Arrays.asList(clazz.getInterfaces()).contains(Comparable.class)) {
                Iterator iterator = fields.iterator();
                while (iterator.hasNext()) {
                    if (((Comparable)iterator.next()).compareTo(value) < 0) {
                        iterator.remove();
                    }
                }
            }
            return this;
        }

        public ListHelperNodes<E> lt(E value) {
            if (Arrays.asList(clazz.getInterfaces()).contains(Comparable.class)) {
                Iterator iterator = fields.iterator();
                while (iterator.hasNext()) {
                    if (((Comparable)iterator.next()).compareTo(value) >= 0) {
                        iterator.remove();
                    }
                }
            }
            return this;
        }

        public ListHelperNodes<E> le(E value) {
            if (Arrays.asList(clazz.getInterfaces()).contains(Comparable.class)) {
                Iterator iterator = fields.iterator();
                while (iterator.hasNext()) {
                    if (((Comparable)iterator.next()).compareTo(value) > 0) {
                        iterator.remove();
                    }
                }
            }
            return this;
        }

        public ListHelperNodes<E> eq(E value) {
            if (Arrays.asList(clazz.getInterfaces()).contains(Comparable.class)) {
                Iterator iterator = fields.iterator();
                while (iterator.hasNext()) {
                    if (((Comparable)iterator.next()).compareTo(value) != 0) {
                        iterator.remove();
                    }
                }
            }
            return this;
        }

        /**
         * Фильтрует список с учётом заданной подстроки.
         * @param value условие фильтрации
         */
        public ListHelperNodes<E> like(E value) {
            if (clazz.equals(String.class)) {
                Iterator iterator = fields.iterator();
                while (iterator.hasNext()) {
                    if (!((String)iterator.next()).contains((String)value)) {
                        iterator.remove();
                    }
                }
            }
            return this;
        }
    }

    public static enum OrderEnum {
        ASC,
        DESC
    }

    public static interface Lambda<C> {
        public C lambda(List<C> list);
    }
}
