package com.griddynamics.jagger.engine.e1.services.data.service;

import com.griddynamics.jagger.util.Decision;

/** Class is a model of test
 *
 * @details
 * TestEntity is used to get test results from database with use of @ref DataService
 *
 * @author
 * Gribov Kirill
 */
public class TestEntity {
    /** Test id (aka task id) - unique id of this test */
    private Long id;

    /** Test name in format [test group name] [test name] */
    private String name;

    /** Test description */
    private String description;

    /** Description of the load for this test */
    private String load;

    /** Description of the termination strategy for this test */
    private String terminationStrategy;

    /** Start date of the test */
    private String startDate;

    /** Index of test group where this test belongs */
    private Integer testGroupIndex;

    /** Status of execution of this test. FATAL when test failed during execution (f.e. due to some workload configuration timeout) */
    private Decision testExecutionStatus;

    /** Get test name in format [test group name] [test name] */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Get description of the load for this test */
    public String getLoad() {
        return load;
    }

    public void setLoad(String load) {
        this.load = load;
    }

    /** Get description of the termination strategy for this test */
    public String getTerminationStrategy() {
        return terminationStrategy;
    }

    public void setTerminationStrategy(String terminationStrategy) {
        this.terminationStrategy = terminationStrategy;
    }

    /** Get test id (aka task id) - unique id of this test */
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** Get test description */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** Get start date */
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    /** Get index of test group where this test was executed */
    public Integer getTestGroupIndex() {
        return testGroupIndex;
    }

    public void setTestGroupIndex(Integer testGroupIndex) {
        this.testGroupIndex = testGroupIndex;
    }

    /** Get status of this test execution */
    public Decision getTestExecutionStatus() {
        return testExecutionStatus;
    }

    public void setTestExecutionStatus(Decision testExecutionStatus) {
        this.testExecutionStatus = testExecutionStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestEntity that = (TestEntity) o;

        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (load != null ? !load.equals(that.load) : that.load != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) return false;
        if (terminationStrategy != null ? !terminationStrategy.equals(that.terminationStrategy) : that.terminationStrategy != null)
            return false;
        if (testExecutionStatus != that.testExecutionStatus) return false;
        if (testGroupIndex != null ? !testGroupIndex.equals(that.testGroupIndex) : that.testGroupIndex != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (load != null ? load.hashCode() : 0);
        result = 31 * result + (terminationStrategy != null ? terminationStrategy.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (testGroupIndex != null ? testGroupIndex.hashCode() : 0);
        result = 31 * result + (testExecutionStatus != null ? testExecutionStatus.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TestEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", load='" + load + '\'' +
                ", terminationStrategy='" + terminationStrategy + '\'' +
                ", startDate='" + startDate + '\'' +
                ", testGroupIndex=" + testGroupIndex +
                ", testExecutionStatus=" + testExecutionStatus +
                '}';
    }
}