package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DispatchConfirmationCommand {
    private Date dispatchDate;
    private List<DispatchTrackingCommand> tracking = new ArrayList<DispatchTrackingCommand>();
    public Date getDispatchDate() { return dispatchDate; }
    public void setDispatchDate(Date date) { dispatchDate = date; }
    public List<DispatchTrackingCommand> getTracking() { return tracking; }
    public void setTracking(List<DispatchTrackingCommand> tracking) { this.tracking = tracking; }
}
