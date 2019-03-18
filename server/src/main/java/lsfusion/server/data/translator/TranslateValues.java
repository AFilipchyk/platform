package lsfusion.server.data.translator;

public interface TranslateValues<T extends TranslateValues<T>> {

    T translateValues(MapValuesTranslate translate);

    // трансляция с "удалением" values, нужно для борьбы с memoryLeak'ами
    // реализация спорная, так как ассертит что mapp'инг против которого идет борьба всегда вверху
    T translateRemoveValues(MapValuesTranslate translate);

}
