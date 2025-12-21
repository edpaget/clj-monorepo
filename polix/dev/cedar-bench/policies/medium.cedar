permit(
    principal is User,
    action == Action::"access",
    resource
)
when {
    principal.role == "admin" &&
    principal.level > 5 &&
    (principal.status == "active" || principal.status == "pending") &&
    principal.age < 65 &&
    principal.score >= 80
};
