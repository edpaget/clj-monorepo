// Superadmin path
permit(principal is User, action == Action::"access", resource)
when { principal.role == "superadmin" && principal.clearance > 9 };

// Admin path
permit(principal is User, action == Action::"access", resource)
when {
    principal.role == "admin" &&
    principal.level > 10 &&
    principal.status == "active" &&
    (principal.department == "security" ||
     (principal.department == "engineering" && principal.tenure > 5))
};

// Moderator path
permit(principal is User, action == Action::"access", resource)
when {
    principal.role == "moderator" &&
    ((principal.karma > 1000 && principal.warnings < 3) ||
     (principal.reputation > 500 && principal.verified)) &&
    (principal.region == "us" || principal.region == "eu" || principal.region == "apac") &&
    !(principal["restricted-flag"] == "flagged" || principal["restricted-flag"] == "suspended")
};

// User path
permit(principal is User, action == Action::"access", resource)
when {
    principal.role == "user" &&
    principal["account-age"] > 365 &&
    principal["trust-score"] >= 90 &&
    (principal.subscription == "premium" || principal.subscription == "enterprise") &&
    !principal.trial
};
