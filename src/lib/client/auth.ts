"use client";

import type { AccountUser } from "@/lib/library-types";
import { useJobs } from "@/lib/store/jobs";
import { useProject } from "@/lib/store/project";
import { ApiError } from "./api";

const LEGACY_OWNER_KEY = "nigixmosha.owner";

function legacyOwner() {
  try {
    return localStorage.getItem(LEGACY_OWNER_KEY);
  } catch {
    return null;
  }
}

function clearLegacyOwner() {
  try {
    localStorage.removeItem(LEGACY_OWNER_KEY);
  } catch {}
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = (await res.json().catch(() => ({}))) as T & { error?: string };
  if (!res.ok) throw new ApiError(data.error ?? `Request failed (${res.status})`, res.status);
  return data;
}

export async function signup(username: string, password: string, displayName: string) {
  const { user } = await post<{ user: AccountUser }>("/api/auth/signup", {
    username,
    password,
    displayName,
    anonymousId: legacyOwner(),
  });
  clearLegacyOwner();
  return user;
}

export async function login(username: string, password: string) {
  const { user } = await post<{ user: AccountUser }>("/api/auth/login", {
    username,
    password,
    anonymousId: legacyOwner(),
  });
  clearLegacyOwner();
  return user;
}

export const logout = (all = false) => post<{ ok: boolean }>("/api/auth/logout", { all });

export const changePassword = (current: string, next: string) =>
  post<{ ok: boolean }>("/api/auth/password", { current, next });

export async function deleteAccount(password: string) {
  const res = await fetch("/api/auth/account", {
    method: "DELETE",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ password }),
  });
  const data = (await res.json().catch(() => ({}))) as { error?: string };
  if (!res.ok) throw new ApiError(data.error ?? `Request failed (${res.status})`, res.status);
}

export async function fetchMe(): Promise<AccountUser | null> {
  try {
    const res = await fetch("/api/auth/me", { cache: "no-store" });
    const data = (await res.json()) as { user: AccountUser | null };
    return data.user;
  } catch {
    return null;
  }
}

export function resetLocalSession() {
  const jobs = useJobs.getState();
  jobs.cancelDirect();
  jobs.cancelProduce();
  jobs.stopPreview();
  useProject.getState().reset();
}
