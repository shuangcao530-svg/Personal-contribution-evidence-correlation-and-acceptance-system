package cn.edu.contribution;

import java.util.List;

public final class ApiModels {
    private ApiModels() {}
    public record User(long id, String username, String name, String role) {
        public boolean teacher() { return "TEACHER".equals(role); }
    }
    public record LoginInput(String username, String password) {}
    public record ProjectInput(String name, String description, List<Long> memberIds) {}
    public record TaskInput(Long projectId, String title, String description, Long assigneeId, String dueDate, List<String> criteria) {}
    public record LinkInput(Long projectId, String title, String url) {}
    public record ReferenceInput(Long evidenceId, Long criterionId, String location, String note) {}
    public record ContributionInput(Long taskId, String title, String description, List<ReferenceInput> evidence) {}
    public record RevisionInput(String title, String description, List<ReferenceInput> evidence) {}
    public record ReviewItemInput(Long criterionId, Boolean passed, String comment) {}
    public record ReviewInput(Long revisionId, String decision, String comment, List<ReviewItemInput> items) {}
}
