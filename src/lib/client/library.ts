import type {
  AudioUploadPayload,
  ProjectPayload,
  ProjectRecord,
  ProjectSummary,
  UploadTicket,
} from "@/lib/library-types";
import { fail } from "./api";

async function call<T>(path: string, init: { method?: string; body?: string } = {}): Promise<T> {
  const res = await fetch(path, { ...init, headers: { "content-type": "application/json" } });
  if (!res.ok) await fail(res);
  return res.json() as Promise<T>;
}

export const listProjects = () => call<{ projects: ProjectSummary[] }>("/api/projects").then((r) => r.projects);

export const getProject = (id: string) => call<{ project: ProjectRecord }>(`/api/projects/${id}`).then((r) => r.project);

export const createProject = (payload: ProjectPayload) =>
  call<{ id: string }>("/api/projects", { method: "POST", body: JSON.stringify(payload) }).then((r) => r.id);

export const updateProject = (id: string, payload: ProjectPayload & { audio?: AudioUploadPayload }) =>
  call<{ ok?: boolean; project?: ProjectRecord }>(`/api/projects/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });

export const deleteProject = (id: string) => call<{ ok: boolean }>(`/api/projects/${id}`, { method: "DELETE" });

export async function uploadAudio(
  projectId: string,
  file: Blob,
  onProgress?: (p: number) => void,
): Promise<{ publicId: string; version: number; bytes: number }> {
  const ticket = await call<UploadTicket>("/api/uploads/sign", { method: "POST", body: JSON.stringify({ projectId }) });
  if (file.size > ticket.maxBytes) throw new Error("This audiobook is too large for cloud storage. Download it instead.");
  const form = new FormData();
  form.append("file", file, "audiobook.wav");
  for (const [k, v] of Object.entries(ticket.params)) form.append(k, String(v));
  form.append("api_key", ticket.apiKey);
  form.append("signature", ticket.signature);
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", ticket.uploadUrl);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(e.loaded / e.total);
    };
    xhr.onload = () => {
      let data: { public_id?: string; version?: number; bytes?: number; error?: { message?: string } } = {};
      try {
        data = JSON.parse(xhr.responseText);
      } catch {}
      if (xhr.status >= 200 && xhr.status < 300 && data.public_id && data.version) {
        resolve({ publicId: data.public_id, version: data.version, bytes: data.bytes ?? file.size });
      } else {
        reject(new Error(data.error?.message ?? `Audio upload failed (${xhr.status})`));
      }
    };
    xhr.onerror = () => reject(new Error("Network error while uploading the audiobook"));
    xhr.send(form);
  });
}
