package com.sayyogames.yocat.entitlement.api;

/**
 * &lt;&lt;Public DTO&gt;&gt; a single owned entitlement exposed by the entitlement BC's Public NamedInterface
 * {@code api} (EDD §4.1). Rendered by {@code GET /entitlements} and aggregated into {@code GET /auth/me};
 * carries only presentation-safe fields (no aggregate leak).
 *
 * @param kind       the entitlement kind, lower-case (cosmetic/dream/scenario)
 * @param itemId     the owned item id
 * @param acquiredAt ISO-8601 acquisition instant, or {@code null} when unknown
 */
public record OwnedEntitlementView(String kind, String itemId, String acquiredAt) {
}
