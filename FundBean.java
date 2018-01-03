package fundSpider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Administrator on 2016/9/12.
 */
public class FundBean {

    String fundCode;
    String fundName;
    String createdTime;
    String fundManager;
    String fee;
    String status;
    String fundUrl;
    String detailUrl;
    private List<FundDetail> details = Collections.synchronizedList(new ArrayList<FundDetail>());

    public List<FundDetail> getDetails() {
        return details;
    }

    public String getFundCode() {
        return fundCode;
    }

    public void setFundCode(String fundCode) {
        this.fundCode = fundCode;
    }

    public String getFundName() {
        return fundName;
    }

    public void setFundName(String fundName) {
        this.fundName = fundName;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getFundManager() {
        return fundManager;
    }

    public void setFundManager(String fundManager) {
        this.fundManager = fundManager;
    }

    public String getFee() {
        return fee;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public void setDetailUrl(String detailUrl) {
        this.detailUrl = detailUrl;
    }

    public String getFundUrl() {
        return fundUrl;
    }

    public void setFundUrl(String fundUrl) {
        this.fundUrl = fundUrl;
    }
}
