package fundSpider;

import java.util.Date;

/**
 * Created by shujiaw on 12/26/2017.
 */
public class FundDetail {
    private Date valueDate;
    private Double value;
    private Double aggregateValue;
    private Double dailyGrowth;

    public Date getValueDate() {
        return valueDate;
    }

    public void setValueDate(Date valueDate) {
        this.valueDate = valueDate;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public Double getAggregateValue() {
        return aggregateValue;
    }

    public void setAggregateValue(Double aggregateValue) {
        this.aggregateValue = aggregateValue;
    }

    public Double getDailyGrowth() {
        return dailyGrowth;
    }

    public void setDailyGrowth(Double dailyGrowth) {
        this.dailyGrowth = dailyGrowth;
    }
}
