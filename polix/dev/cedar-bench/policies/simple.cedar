permit(
    principal is User,
    action == Action::"access",
    resource
)
when { principal.role == "admin" };
