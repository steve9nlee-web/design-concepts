const express = require('express');
const ExcelJS = require('exceljs');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3456;

// Store records in Google Drive (synced by Google Drive for Desktop).
// Falls back to a local folder if the Drive letter is not mounted.
const DRIVE_DIR = 'I:\\My Drive\\Receipt Logger';
const LOCAL_DIR = path.join(__dirname, 'data');

let DATA_DIR;
try {
  fs.mkdirSync(DRIVE_DIR, { recursive: true });
  DATA_DIR = DRIVE_DIR;
} catch (e) {
  console.warn('Google Drive (I:) not available, storing locally instead:', e.message);
  fs.mkdirSync(LOCAL_DIR, { recursive: true });
  DATA_DIR = LOCAL_DIR;
}

const PHOTOS_DIR = path.join(DATA_DIR, 'photos');
const EXCEL_FILE = path.join(DATA_DIR, 'records.xlsx');
const SHEET_NAME = 'Received Items';

if (!fs.existsSync(PHOTOS_DIR)) fs.mkdirSync(PHOTOS_DIR, { recursive: true });

// Allow the Android app (different origin) to call this server
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

app.use(express.json({ limit: '25mb' }));
app.use(express.static(path.join(__dirname, 'public')));
app.use('/photos', express.static(PHOTOS_DIR));

const RECEIPT_TYPES = ['Courier', 'Walk-in', 'Collect'];
const DEPARTMENTS = ['Microbiology', 'Scientific'];

const COLUMNS = [
  { header: 'No.', key: 'no', width: 6 },
  { header: 'Date Received', key: 'dateReceived', width: 14 },
  { header: 'Time Received', key: 'timeReceived', width: 14 },
  { header: 'Acknowledgment of Receipt', key: 'receiptType', width: 24 },
  { header: 'Department', key: 'department', width: 16 },
  { header: 'Remarks', key: 'remarks', width: 30 },
  { header: 'Photo File', key: 'photoFile', width: 40 },
];

async function loadWorkbook() {
  const workbook = new ExcelJS.Workbook();
  if (fs.existsSync(EXCEL_FILE)) {
    await workbook.xlsx.readFile(EXCEL_FILE);
    const sheet = workbook.getWorksheet(SHEET_NAME) || workbook.worksheets[0];
    sheet.columns = COLUMNS;
    return { workbook, sheet };
  }
  const sheet = workbook.addWorksheet(SHEET_NAME);
  sheet.columns = COLUMNS;
  sheet.getRow(1).font = { bold: true };
  return { workbook, sheet };
}

// Serialize writes so two quick submissions don't corrupt the file
let writeQueue = Promise.resolve();

function pad(n) {
  return String(n).padStart(2, '0');
}

app.post('/api/records', (req, res) => {
  writeQueue = writeQueue.then(async () => {
    try {
      const { photo, takenAt, receiptType, department, remarks } = req.body || {};

      if (!photo || !/^data:image\/(jpeg|png|webp);base64,/.test(photo)) {
        return res.status(400).json({ error: 'A captured photo is required.' });
      }
      if (!RECEIPT_TYPES.includes(receiptType)) {
        return res.status(400).json({ error: 'Acknowledgment of receipt must be Courier, Walk-in or Collect.' });
      }
      if (!DEPARTMENTS.includes(department)) {
        return res.status(400).json({ error: 'Department must be Microbiology or Scientific.' });
      }

      const taken = takenAt ? new Date(takenAt) : new Date();
      const when = isNaN(taken.getTime()) ? new Date() : taken;

      const dateReceived = `${when.getFullYear()}-${pad(when.getMonth() + 1)}-${pad(when.getDate())}`;
      const timeReceived = `${pad(when.getHours())}:${pad(when.getMinutes())}:${pad(when.getSeconds())}`;

      const ext = photo.startsWith('data:image/png') ? 'png' : photo.startsWith('data:image/webp') ? 'webp' : 'jpg';
      const stamp = `${dateReceived.replace(/-/g, '')}_${timeReceived.replace(/:/g, '')}`;
      const fileName = `${stamp}_${department}_${receiptType.replace(/\s+/g, '')}.${ext}`;
      const base64 = photo.split(',')[1];
      fs.writeFileSync(path.join(PHOTOS_DIR, fileName), Buffer.from(base64, 'base64'));

      const { workbook, sheet } = await loadWorkbook();
      const no = sheet.rowCount; // header is row 1, so first record gets No. 1
      sheet.addRow({
        no,
        dateReceived,
        timeReceived,
        receiptType,
        department,
        remarks: (remarks || '').trim(),
        photoFile: fileName,
      });
      await workbook.xlsx.writeFile(EXCEL_FILE);

      res.json({ ok: true, no, dateReceived, timeReceived, receiptType, department, photoFile: fileName });
    } catch (err) {
      console.error('Failed to save record:', err);
      if (err.code === 'EBUSY' || (err.message && err.message.includes('EBUSY'))) {
        return res.status(409).json({ error: 'records.xlsx is open in Excel. Close it and try again.' });
      }
      res.status(500).json({ error: 'Failed to save the record. ' + err.message });
    }
  });
});

app.get('/api/records', async (req, res) => {
  try {
    if (!fs.existsSync(EXCEL_FILE)) return res.json({ records: [] });
    const { sheet } = await loadWorkbook();
    const records = [];
    sheet.eachRow((row, rowNumber) => {
      if (rowNumber === 1) return;
      records.push({
        no: row.getCell('no').value,
        dateReceived: row.getCell('dateReceived').value,
        timeReceived: row.getCell('timeReceived').value,
        receiptType: row.getCell('receiptType').value,
        department: row.getCell('department').value,
        remarks: row.getCell('remarks').value,
        photoFile: row.getCell('photoFile').value,
      });
    });
    res.json({ records: records.reverse() });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.listen(PORT, () => {
  console.log(`Receipt logger running at http://localhost:${PORT}`);
  console.log(`Excel file: ${EXCEL_FILE}`);
  console.log(`Photos folder: ${PHOTOS_DIR}`);
});
