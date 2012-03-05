package platform.server;

import platform.base.ApiResourceBundle;
import platform.server.logics.ServerResourceBundle;

public class Settings {

    public static Settings instance;
    private String locale;

    private int innerGroupExprs = 0; // использовать Subquery Expressions
    public int getInnerGroupExprs() {
        return innerGroupExprs;
    }

    public void setInnerGroupExprs(int innerGroupExprs) {
        this.innerGroupExprs = innerGroupExprs;
    }

    public int packOnCacheComplexity = 300;

    public int getPackOnCacheComplexity() {
        return packOnCacheComplexity;
    }

    public void setPackOnCacheComplexity(int packOnCacheComplexity) {
        this.packOnCacheComplexity = packOnCacheComplexity;
    }

    private int mapInnerMaxIterations = 4;

    public int getMapInnerMaxIterations() {
        return mapInnerMaxIterations;
    }

    public void setMapInnerMaxIterations(int mapInnerMaxIterations) {
        this.mapInnerMaxIterations = mapInnerMaxIterations;
    }

    // обозначает что если компилятор видет включающие join'ы (J1, J2, ... Jk) (J1,...J2, ... Jk,.. Jn) он будет выполнять все в первом подмножестве, предполагая что возникающий OR разберет SQL сервер что мягко говоря не так
    private boolean compileMeans = true;

    // обозначает что компилятор будет проталкивать внутрь order подзапросов, общее условие
    private boolean pushOrderWhere = true;

    // обозначает что при проверке условия на TRUE не будет преобразовывать A cmp B в 3 противоположных NOT'а как правильно, а будет использовать эвристику
    private boolean simpleCheckCompare = true;

    // обозначает что на следствия (и отрицания) условия будет проверять когда остались только термы, не делая этого на промежуточных уровнях
    private boolean checkFollowsWhenObjects = false;

    // будет ли оптимизатор пытаться перестраивать условия по правилу X OR (Y AND Z) и X=>Y, то Y AND (X OR Z)
    private boolean restructWhereOnMeans = false;

    // будет ли оптимизатор разбивать группирующие выражения, чтобы не было FULL JOIN и UNION ALL
    private boolean splitSelectGroupInnerJoins = false;

    // будет ли оптимизатор разбивать inner join'ы по статистике
    private boolean splitGroupStatInnerJoins = false;

    // будет ли компилятор вместо UNION (когда UNION ALL не удается построить) использовать FULL JOIN
    boolean useFJInsteadOfUnion = false;

    // будет ли оптимизатор разбивать группирующие выражения на максимум, так чтобы в группируемом выражении не было бы Case'ов
    private boolean splitGroupSelectExprcases = false;

    // будет ли высчитываться что именно изменилось в группирующих свойствах или же будет считаться что изменилось все
    private boolean calculateGroupDataChanged = false;

    // не использовать инкрементную логику в группирующем свойстве на максимум
    private boolean noIncrementMaxGroupProperty = true;

    // использовать применение изменений "по одному"
    private boolean enableApplySingleStored = true;

    private boolean editLogicalOnSingleClick = false;
    private boolean editActionOnSingleClick = false;

    public boolean isEnableApplySingleStored() {
        return enableApplySingleStored;
    }

    public void setEnableApplySingleStored(boolean enableApplySingleStored) {
        this.enableApplySingleStored = enableApplySingleStored;
    }

    public boolean isPushOrderWhere() {
        return pushOrderWhere;
    }

    public boolean isSplitSelectGroupInnerJoins() {
        return splitSelectGroupInnerJoins;
    }

    public void setSplitSelectGroupInnerJoins(boolean splitSelectGroupInnerJoins) {
        this.splitSelectGroupInnerJoins = splitSelectGroupInnerJoins;
    }

    public boolean isSplitGroupStatInnerJoins() {
        return splitGroupStatInnerJoins;
    }

    public void setSplitGroupStatInnerJoins(boolean splitGroupStatInnerJoins) {
        this.splitGroupStatInnerJoins = splitGroupStatInnerJoins;
    }

    public boolean isUseFJInsteadOfUnion() {
        return useFJInsteadOfUnion;
    }

    public void setUseFJInsteadOfUnion(boolean useFJInsteadOfUnion) {
        this.useFJInsteadOfUnion = useFJInsteadOfUnion;
    }

    public void setPushOrderWhere(boolean pushOrderWhere) {
        this.pushOrderWhere = pushOrderWhere;
    }

    public boolean isSimpleCheckCompare() {
        return simpleCheckCompare;
    }

    public Boolean getEditLogicalOnSingleClick() {
       return editLogicalOnSingleClick;
    }

    public void setEditLogicalOnSingleClick(boolean editLogicalOnSingleClick) {
       this.editLogicalOnSingleClick = editLogicalOnSingleClick;
    }

    public Boolean getEditActionClassOnSingleClick() {
       return editActionOnSingleClick;
    }

    public void setEditActionOnSingleClick(boolean editActionOnSingleClick) {
            this.editActionOnSingleClick = editActionOnSingleClick;

    }

    public void setSimpleCheckCompare(boolean simpleCheckCompare) {
        this.simpleCheckCompare = simpleCheckCompare;
    }

    public boolean isCheckFollowsWhenObjects() {
        return checkFollowsWhenObjects;
    }

    public void setCheckFollowsWhenObjects(boolean checkFollowsWhenObjects) {
        this.checkFollowsWhenObjects = checkFollowsWhenObjects;
    }

    public boolean isRestructWhereOnMeans() {
        return restructWhereOnMeans;
    }

    public void setRestructWhereOnMeans(boolean restructWhereOnMeans) {
        this.restructWhereOnMeans = restructWhereOnMeans;
    }

    public boolean isSplitGroupSelectExprcases() {
        return splitGroupSelectExprcases;
    }

    public void setSplitGroupSelectExprcases(boolean splitGroupSelectExprcases) {
        this.splitGroupSelectExprcases = splitGroupSelectExprcases;
    }

    public boolean isCalculateGroupDataChanged() {
        return calculateGroupDataChanged;
    }

    public void setCalculateGroupDataChanged(boolean calculateGroupDataChanged) {
        this.calculateGroupDataChanged = calculateGroupDataChanged;
    }

    public boolean isNoIncrementMaxGroupProperty() {
        return noIncrementMaxGroupProperty;
    }

    public void setNoIncrementMaxGroupProperty(boolean noIncrementMaxGroupProperty) {
        this.noIncrementMaxGroupProperty = noIncrementMaxGroupProperty;
    }

    public boolean isCompileMeans() {
        return compileMeans;
    }

    public void setCompileMeans(boolean compileMeans) {
        this.compileMeans = compileMeans;
    }

    private int freeConnections = 5;

    public int getFreeConnections() {
        return freeConnections;
    }

    public void setFreeConnections(int freeConnections) {
        this.freeConnections = freeConnections;
    }

    private boolean commonUnique = false;

    public boolean isCommonUnique() {
        return commonUnique;
    }

    public void setCommonUnique(boolean commonUnique) {
        this.commonUnique = commonUnique;
    }

    private boolean disablePoolConnections = false;

    public boolean isDisablePoolConnections() {
        return disablePoolConnections;
    }

    public void setDisablePoolConnections(boolean disablePoolConnections) {
        this.disablePoolConnections = disablePoolConnections;
    }

    private boolean disableSumGroupNotZero = false;

    public boolean isDisableSumGroupNotZero() {
        return disableSumGroupNotZero;
    }

    public void setDisableSumGroupNotZero(boolean disableSumGroupNotZero) {
        this.disableSumGroupNotZero = disableSumGroupNotZero;
    }

    private int usedChangesCacheLimit = 20;

    public int getUsedChangesCacheLimit() {
        return usedChangesCacheLimit;
    }

    public void setUsedChangesCacheLimit(int usedChangesCacheLimit) {
        this.usedChangesCacheLimit = usedChangesCacheLimit;
    }

    public void setLocale(String locale) {
        this.locale = locale;
        ServerResourceBundle.load(locale);
        ApiResourceBundle.load(locale);
    }

    // максимум сколько свойств вместе будет применяться в базу
    private int splitIncrementApply = 10;

    public int getSplitIncrementApply() {
        return splitIncrementApply;
    }

    public void setSplitIncrementApply(int splitIncrementApply) {
        this.splitIncrementApply = splitIncrementApply;
    }

    private int statDegree = 100;

    public int getStatDegree() {
        return statDegree;
    }

    public void setStatDegree(int statDegree) {
        this.statDegree = statDegree;
    }

    private boolean killThread = true;

    public boolean getKillThread() {
        return killThread;
    }

    public void setKillThread(boolean killThread) {
        this.killThread = killThread;
    }

    private boolean useUniPass;

    public void setUseUniPass(boolean useUniPass) {
        this.useUniPass = useUniPass;
    }

    public boolean getUseUniPass() {
        return useUniPass;
    }
    
    private boolean useQueryExpr = true;

    public boolean isUseQueryExpr() {
        return useQueryExpr;
    }

    public void setUseQueryExpr(boolean useQueryExpr) {
        this.useQueryExpr = useQueryExpr;
    }

    private int limitWhereJoinsCount = 20;
    private int limitWhereJoinsComplexity = 200;
    private int limitClassWhereCount = 40;
    private int limitClassWhereComplexity = 400;
    private int limitWhereJoinPack = 300;
    private int limitExclusiveCount = 7; // когда вообще не пытаться строить exclusive (count)
    private int limitExclusiveComplexity = 100; // когда вообще не пытаться строить exclusive (complexity)
    private int limitExclusiveSimpleCount = 10; // когда строить exclusive без рекурсии (count)
    private int limitExclusiveSimpleComplexity = 100; // когда строить exclusive без рекурсии (complexity)

    private int limitIncrementCoeff = 1;
    private int limitHintIncrementComplexity = 50;
    private int limitGrowthIncrementComplexity = 1;
    private int limitHintIncrementStat = 1000;
    private int limitHintNoUpdateComplexity = 2000;
    private int limitWrapComplexity = 200;

    public int getLimitWhereJoinsCount() {
        return limitWhereJoinsCount;
    }
    public void setLimitWhereJoinsCount(int limitWhereJoinsCount) {
        this.limitWhereJoinsCount = limitWhereJoinsCount;
    }
    public int getLimitWhereJoinsComplexity() {
        return limitWhereJoinsComplexity;
    }
    public void setLimitWhereJoinsComplexity(int limitWhereJoinsComplexity) {
        this.limitWhereJoinsComplexity = limitWhereJoinsComplexity;
    }
    public int getLimitClassWhereCount() {
        return limitClassWhereCount;
    }
    public void setLimitClassWhereCount(int limitClassWhereCount) {
        this.limitClassWhereCount = limitClassWhereCount;
    }
    public int getLimitClassWhereComplexity() {
        return limitClassWhereComplexity;
    }
    public void setLimitClassWhereComplexity(int limitClassWhereComplexity) {
        this.limitClassWhereComplexity = limitClassWhereComplexity;
    }
    public int getLimitWhereJoinPack() {
        return limitWhereJoinPack;
    }
    public void setLimitWhereJoinPack(int limitWhereJoinPack) {
        this.limitWhereJoinPack = limitWhereJoinPack;
    }
    public int getLimitHintIncrementComplexity() {
        return limitHintIncrementComplexity * limitIncrementCoeff;
    }
    public void setLimitHintIncrementComplexity(int limitHintIncrementComplexity) {
        this.limitHintIncrementComplexity = limitHintIncrementComplexity;
    }
    public int getLimitHintIncrementStat() {
        return limitHintIncrementStat;
    }
    public void setLimitHintIncrementStat(int limitHintIncrementStat) {
        this.limitHintIncrementStat = limitHintIncrementStat;
    }
    public int getLimitHintNoUpdateComplexity() {
        return limitHintNoUpdateComplexity * limitIncrementCoeff;
    }
    public void setLimitHintNoUpdateComplexity(int limitHintNoUpdateComplexity) {
        this.limitHintNoUpdateComplexity = limitHintNoUpdateComplexity;
    }
    public int getLimitWrapComplexity() {
        return limitWrapComplexity * limitIncrementCoeff;
    }
    public void setLimitWrapComplexity(int limitWrapComplexity) {
        this.limitWrapComplexity = limitWrapComplexity;
    }
    public int getLimitGrowthIncrementComplexity() {
        return limitGrowthIncrementComplexity;
    }
    public void setLimitGrowthIncrementComplexity(int limitGrowthIncrementComplexity) {
        this.limitGrowthIncrementComplexity = limitGrowthIncrementComplexity;
    }
    public int getLimitExclusiveCount() {
        return limitExclusiveCount;
    }
    public void setLimitExclusiveCount(int limitExclusiveCount) {
        this.limitExclusiveCount = limitExclusiveCount;
    }
    public int getLimitExclusiveSimpleCount() {
        return limitExclusiveSimpleCount;
    }
    public void setLimitExclusiveSimpleCount(int limitExclusiveSimpleCount) {
        this.limitExclusiveSimpleCount = limitExclusiveSimpleCount;
    }
    public int getLimitExclusiveSimpleComplexity() {
        return limitExclusiveSimpleComplexity;
    }
    public void setLimitExclusiveSimpleComplexity(int limitExclusiveSimpleComplexity) {
        this.limitExclusiveSimpleComplexity = limitExclusiveSimpleComplexity;
    }
    public int getLimitExclusiveComplexity() {
        return limitExclusiveComplexity;
    }
    public void setLimitExclusiveComplexity(int limitExclusiveComplexity) {
        this.limitExclusiveComplexity = limitExclusiveComplexity;
    }
}
