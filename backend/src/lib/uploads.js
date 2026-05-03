import multer from 'multer';

export function createMemoryUpload({ fileSizeMb }) {
  return multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: fileSizeMb * 1024 * 1024 }
  });
}
