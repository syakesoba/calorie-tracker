"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth";

const LINKS = [
  { href: "/dashboard", label: "ダッシュボード" },
  { href: "/record", label: "食事を記録" },
  { href: "/setup", label: "目標設定" },
];

export function Nav() {
  const pathname = usePathname();
  const { user, signOut } = useAuth();

  return (
    <nav className="nav">
      <div className="navInner">
        <span className="navBrand">カロリー管理</span>
        {LINKS.map((link) => (
          <Link
            key={link.href}
            href={link.href}
            className={`navLink${pathname === link.href ? " active" : ""}`}
          >
            {link.label}
          </Link>
        ))}
        {user && (
          <span className="navUser">
            {user.displayName}
            <button type="button" onClick={signOut}>
              ログアウト
            </button>
          </span>
        )}
      </div>
    </nav>
  );
}
