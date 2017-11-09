package nl.slam_it.maven.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Arrays;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectStatus {
    private final ProjectStatusWrapper wrapper;

    @JsonCreator
    public ProjectStatus(@JsonProperty("projectStatus") ProjectStatusWrapper wrapper) {
        this.wrapper = wrapper;
    }

    public String getStatus() {
        return this.wrapper.getStatus();
    }

    public String getErrorsMessage() {
        return Arrays.stream(this.wrapper.getConditions())
          .filter(cond -> "ERROR".equals(cond.getStatus()))
          .map(cond -> {
            return "[" + cond.getMetricKey() + "]: " + cond.getActualValue() + " " + cond.getComparator() + " " + cond.getErrorThreshold();
          })
          .collect(Collectors.joining(", "));
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ProjectStatusWrapper {
    private final String status;
    private final Condition[] conditions;

    @JsonCreator
    public ProjectStatusWrapper(@JsonProperty("status") String status, @JsonProperty("conditions") Condition[] conditions) {
        this.status = status;
        this.conditions = conditions;
    }

    public String getStatus() {
        return status;
    }

    public Condition[] getConditions() {
        return conditions;
    }
}

class Condition {
  @JsonProperty("status")
  private String status;
  @JsonProperty("metricKey")
  private String metricKey;
  @JsonProperty("comparator")
  private String comparator;
  @JsonProperty("periodIndex")
  private String periodIndex;
  @JsonProperty("errorThreshold")
  private String errorThreshold;
  @JsonProperty("warningThreshold")
  private String warningThreshold;
  @JsonProperty("actualValue")
  private String actualValue;

  public String getStatus() {
    return status;
  }

  public String getMetricKey() {
    return metricKey;
  }

  public String getComparator() {
    return comparator;
  }

  public String getPeriodIndex() {
    return periodIndex;
  }

  public String getErrorThreshold() {
    return errorThreshold;
  }

  public String getWarningThreshold() {
    return warningThreshold;
  }

  public String getActualValue() {
    return actualValue;
  }
}
