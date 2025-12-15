/*     */ package it.elsag.rbs.stc.business;
/*     */ 
/*     */ import it.elsag.common.LogHelper;
/*     */ import it.elsag.common.control.CallerInfo;
/*     */ import it.elsag.common.control.ErrorItem;
/*     */ import it.elsag.common.control.ReturnInfo;
/*     */ import it.elsag.common.engine.orc.BricContainer;
/*     */ import it.elsag.common.engine.orc.RbsOrc;
/*     */ import it.elsag.common.exceptions.ServiceException;
/*     */ import it.elsag.common.utils.RandomGUID;
/*     */ import java.io.BufferedReader;
/*     */ import java.io.InputStream;
/*     */ import java.io.InputStreamReader;
/*     */ import java.sql.SQLException;
/*     */ import java.text.SimpleDateFormat;
/*     */ import java.util.ArrayList;
/*     */ import java.util.Date;
/*     */ import java.util.GregorianCalendar;
/*     */ import java.util.Iterator;
/*     */ import java.util.Locale;
/*     */ import javax.sql.rowset.CachedRowSet;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public class STCFilesLocal
/*     */ {
/*  33 */   private static LogHelper log = LogHelper.getInstance(STCFilesLocal.class);
/*     */ 
/*     */ 
/*     */   
/*     */   public ReturnInfo UploadSTCFile(CallerInfo caller, String fileName, InputStream fileStream) throws ServiceException {
/*     */     try {
/*  39 */       ReturnInfo retInfo = new ReturnInfo();
/*     */ 
/*     */       
/*  42 */       if (caller == null || caller.getUser() == null) {
/*     */         
/*  44 */         retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*  45 */         retInfo.setReturnCode("1");
/*  46 */         return retInfo;
/*     */       } 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/*  52 */       if (fileName != null && fileName.length() == 0) {
/*  53 */         retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "11007", null));
/*  54 */         retInfo.setReturnCode("1");
/*  55 */         return retInfo;
/*     */       } 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/*  61 */       retInfo = CheckFileDuplicates(caller, fileName);
/*  62 */       if (retInfo.getReturnCode().equals("1")) {
/*  63 */         return retInfo;
/*     */       }
/*  65 */       ArrayList _alFileRecords = new ArrayList();
/*     */       
/*  67 */       retInfo = ValidateInputFile(fileStream, _alFileRecords);
/*  68 */       if (retInfo.getReturnCode().equals("1")) {
/*  69 */         return retInfo;
/*     */       }
/*     */ 
/*     */ 
/*     */       
/*  74 */       if (_alFileRecords.size() < 1) {
/*  75 */         retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "11008", null));
/*  76 */         retInfo.setReturnCode("1");
/*  77 */         return retInfo;
/*     */       } 
/*     */ 
/*     */       
/*  81 */       retInfo = SaveFileRecords(caller, fileName, _alFileRecords);
/*  82 */       if (retInfo.getReturnCode().equals("1")) {
/*  83 */         return retInfo;
/*     */       }
/*  85 */       retInfo.setReturnCode("0");
/*  86 */       return retInfo;
/*     */     }
/*  88 */     catch (Exception e) {
/*     */       
/*  90 */       log.error(e.getMessage(), e);
/*  91 */       throw new ServiceException(e.getMessage(), e);
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private ReturnInfo CheckFileDuplicates(CallerInfo caller, String fileName) throws ServiceException {
/* 102 */     ReturnInfo retInfo = new ReturnInfo();
/*     */     
/*     */     try {
/* 105 */       StringBuffer sql = new StringBuffer("SELECT * FROM STC_FILES WHERE C_IST = $(BANK)");
/* 106 */       sql.append(" AND FILE_NAME = '").append(fileName).append("'");
/*     */       
/* 108 */       CachedRowSet crs = RbsOrc.SQLSelect(caller, sql.toString(), new Object[0]);
/* 109 */       if (crs != null && crs.next()) {
/*     */         
/* 111 */         retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "11009", null));
/* 112 */         retInfo.setReturnCode("1");
/* 113 */         return retInfo;
/*     */       } 
/* 115 */     } catch (ServiceException e) {
/* 116 */       log.error(e.getMessage());
/* 117 */       throw e;
/* 118 */     } catch (SQLException e) {
/* 119 */       log.error(e.getMessage());
/* 120 */       throw new ServiceException("Error SQL: CheckFileDuplicates() ", e);
/*     */     } 
/*     */     
/* 123 */     retInfo.setReturnCode("0");
/* 124 */     return retInfo;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private ReturnInfo ValidateInputFile(InputStream IN_fileStream, ArrayList<STCFileRecord> OUT_alFileRecords) throws ServiceException {
/* 131 */     ReturnInfo retInfo = new ReturnInfo();
/*     */     
/* 133 */     int iRecIndex = 0;
/*     */ 
/*     */ 
/*     */ 
/*     */     
/*     */     try {
/* 139 */       BufferedReader reader = new BufferedReader(new InputStreamReader(IN_fileStream, "windows-1252"));
/*     */       String lineRecord;
/* 141 */       while ((lineRecord = reader.readLine()) != null) {
/* 142 */         if (lineRecord.length() == 0) {
/*     */           continue;
/*     */         }
/* 145 */         iRecIndex++;
/*     */ 
/*     */ 
/*     */         
/* 149 */         int iTokenNo = 0;
/* 150 */         STCFileRecord fileRecord = new STCFileRecord();
/*     */         
/* 152 */         StringBuffer item = new StringBuffer();
/*     */         
/* 154 */         for (int i = 0; i < lineRecord.length(); i++) {
/* 155 */           if (lineRecord.charAt(i) == '*') {
/*     */ 
/*     */ 
/*     */             
/* 159 */             FillFileRecord(fileRecord, iTokenNo, item.toString());
/*     */             
/* 161 */             item.setLength(0);
/* 162 */             iTokenNo++;
/*     */           }
/*     */           else {
/*     */             
/* 166 */             if (i == lineRecord.length() - 1) {
/* 167 */               item.append(lineRecord.charAt(i));
/*     */ 
/*     */ 
/*     */ 
/*     */               
/* 172 */               FillFileRecord(fileRecord, iTokenNo, item.toString());
/*     */               
/*     */               break;
/*     */             } 
/*     */             
/* 177 */             item.append(lineRecord.charAt(i));
/*     */           } 
/*     */         } 
/* 180 */         fileRecord.setRecIndex(iRecIndex);
/* 181 */         fileRecord.setFileRecord(lineRecord);
/*     */ 
/*     */ 
/*     */ 
/*     */         
/* 186 */         ValidateFileRecord(fileRecord, retInfo);
/* 187 */         if (retInfo.isError()) {
/* 188 */           log.error("ValidateInputFile: invalid record -> " + lineRecord);
/* 189 */           reader.close();
/*     */           
/* 191 */           return retInfo;
/*     */         } 
/*     */ 
/*     */ 
/*     */ 
/*     */         
/* 197 */         OUT_alFileRecords.add(fileRecord);
/*     */       } 
/* 199 */     } catch (Exception ex) {
/* 200 */       log.error("Error ValidateInputFile - record number: " + iRecIndex + " ", ex);
/* 201 */       ex.printStackTrace();
/* 202 */       throw new ServiceException("Error in ValidateInputFile", ex);
/*     */     } 
/*     */     
/* 205 */     retInfo.setReturnCode("0");
/* 206 */     return retInfo;
/*     */   }
/*     */   
/*     */   public ReturnInfo SaveFileRecords(CallerInfo caller, String fileName, ArrayList arRecords) throws ServiceException {
/* 210 */     ReturnInfo ret = new ReturnInfo();
/* 211 */     String bricKeyIn = "RSTCF.001";
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 217 */     RandomGUID _randomGUID = new RandomGUID();
/* 218 */     String _strRandomGUID = _randomGUID.toString();
/* 219 */     GregorianCalendar _cal = new GregorianCalendar();
/* 220 */     Date _date = _cal.getTime();
/*     */ 
/*     */     
/* 223 */     SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
/* 224 */     String strTm = dateFormat.format(_date);
/*     */     
/* 226 */     if (arRecords.isEmpty()) {
/* 227 */       ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1120", null));
/* 228 */       ret.setReturnCode("1");
/* 229 */       return ret;
/*     */     } 
/*     */     
/* 232 */     BricContainer container = new BricContainer();
/* 233 */     CachedRowSet crs = container.createRowSet(bricKeyIn, "0050");
/*     */     
/*     */     try {
/* 236 */       crs.moveToInsertRow();
/*     */       
/* 238 */       crs.updateString("STC_FILEID", _strRandomGUID);
/* 239 */       crs.updateString("STC_FILENAME", fileName);
/* 240 */       crs.updateString("STC_FILEUPDATE", strTm);
/* 241 */       crs.updateString("STC_FILESTATUS", "00");
/*     */       
/* 243 */       crs.insertRow();
/*     */     }
/* 245 */     catch (SQLException e) {
/* 246 */       throw new ServiceException("Error SQL SaveFileRecords(): " + e.getMessage(), "9000");
/*     */     } 
/*     */     
/* 249 */     bricKeyIn = "RSTCF.002";
/* 250 */     crs = container.createRowSet(bricKeyIn, "0050");
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 255 */     Iterator<STCFileRecord> _iter = arRecords.iterator();
/*     */     
/* 257 */     int recNo = 0;
/*     */ 
/*     */     
/* 260 */     while (_iter.hasNext()) {
/* 261 */       recNo++;
/* 262 */       STCFileRecord _fileRecord = _iter.next();
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 267 */       String govName1256 = _fileRecord.getGovName();
/*     */       
/* 269 */       String fileRecord1256 = _fileRecord.getFileRecord();
/*     */ 
/*     */       
/*     */       try {
/* 273 */         crs.moveToInsertRow();
/*     */         
/* 275 */         crs.updateString("STC_FILEID", _strRandomGUID);
/* 276 */         crs.updateInt("STC_RECINDEX", _fileRecord.getRecIndex());
/* 277 */         crs.updateString("STC_RECTYPE", _fileRecord.getType());
/* 278 */         crs.updateString("STC_RECAMNT", _fileRecord.getAmount());
/* 279 */         crs.updateString("STC_RECACCNO", _fileRecord.getAccountNo());
/* 280 */         crs.updateString("STC_RECCHQNO", _fileRecord.getChequeNo());
/*     */ 
/*     */ 
/*     */ 
/*     */         
/* 285 */         if (_fileRecord.getType().equals("02")) {
/* 286 */           crs.updateString("C_AG", _fileRecord.getCABranch());
/* 287 */           crs.updateString("C_TP_RAPP", _fileRecord.getCAType());
/* 288 */           crs.updateString("N_RAPP", _fileRecord.getCANumber());
/*     */         } 
/* 290 */         crs.updateString("STC_RECGOVNAME", govName1256);
/*     */         
/* 292 */         if (_fileRecord.getDeptCode() != null) {
/* 293 */           crs.updateString("STC_RECGOVDPT", _fileRecord.getDeptCode());
/*     */         }
/* 295 */         crs.updateString("STC_RECSERVTYPE", _fileRecord.getServiceType());
/* 296 */         crs.updateString("STC_RECDATE", _fileRecord.getRecDate());
/* 297 */         crs.updateString("STC_RECBILLERID", _fileRecord.getBillerID());
/* 298 */         crs.updateString("STC_RECBILLCYLE", _fileRecord.getBillCycle());
/* 299 */         crs.updateString("STC_RECSTATUS", "NE");
/* 300 */         crs.updateString("STC_RECUPDATE", strTm);
/* 301 */         crs.updateString("STC_RECSTR", fileRecord1256);
/*     */         
/* 303 */         crs.insertRow();
/*     */       }
/* 305 */       catch (SQLException e) {
/* 306 */         throw new ServiceException("Error SQL SaveFileRecords()[" + recNo + "]: " + e.getMessage(), "9000");
/*     */       } 
/*     */     } 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 319 */     container.ExecTransaction(caller);
/*     */     
/* 321 */     return container.getReturnInfo();
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private void FillFileRecord(STCFileRecord fileRecord, int position, String value) {
/* 328 */     if (value.length() == 0) {
/*     */       return;
/*     */     }
/*     */ 
/*     */ 
/*     */     
/* 334 */     switch (position) {
/*     */       case 0:
/* 336 */         fileRecord.setType(value);
/*     */         return;
/*     */       case 1:
/* 339 */         fileRecord.setAmount(value);
/*     */         return;
/*     */       case 2:
/* 342 */         fileRecord.setAccountNo(value.trim());
/*     */         return;
/*     */       case 3:
/* 345 */         fileRecord.setChequeNo(value.trim());
/*     */         return;
/*     */       case 4:
/* 348 */         fileRecord.setSIB2000CA(value.trim());
/*     */         return;
/*     */       case 5:
/* 351 */         fileRecord.setGovName(value);
/*     */         return;
/*     */       case 6:
/* 354 */         fileRecord.setDeptCode(value);
/*     */         return;
/*     */       case 7:
/* 357 */         fileRecord.setServiceType(value);
/*     */         return;
/*     */       case 8:
/* 360 */         fileRecord.setRecDate(value);
/*     */         return;
/*     */       case 9:
/* 363 */         fileRecord.setBillerID(value);
/*     */         return;
/*     */       case 10:
/* 366 */         fileRecord.setBillCycle(value);
/*     */         return;
/*     */     } 
/* 369 */     log.debug("the position is not valid, value position= " + position);
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private void ValidateFileRecord(STCFileRecord fileRecord, ReturnInfo retInfo) {
/* 377 */     if (fileRecord.getType() == null || fileRecord.getAmount() == null || fileRecord.getAccountNo() == null || 
/* 378 */       fileRecord.getChequeNo() == null || fileRecord.getServiceType() == null || 
/* 379 */       fileRecord.getRecDate() == null || fileRecord.getBillerID() == null || 
/* 380 */       fileRecord.getBillCycle() == null) {
/* 381 */       log.error("ValidateFileRecord: some fields are empty");
/*     */       
/* 383 */       retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "8000", null));
/* 384 */       retInfo.setReturnCode("1");
/*     */ 
/*     */       
/*     */       return;
/*     */     } 
/*     */ 
/*     */     
/* 391 */     if (!fileRecord.getType().equals("01") && !fileRecord.getType().equals("02")) {
/* 392 */       log.error("ValidateFileRecord: invalid record type");
/*     */       
/* 394 */       retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "8000", null));
/* 395 */       retInfo.setReturnCode("1");
/*     */ 
/*     */       
/*     */       return;
/*     */     } 
/*     */ 
/*     */     
/* 402 */     if (fileRecord.getAmount().length() == 0 || fileRecord.getAccountNo().length() == 0 || 
/* 403 */       fileRecord.getChequeNo().length() == 0 || fileRecord.getServiceType().length() == 0 || 
/* 404 */       fileRecord.getRecDate().length() == 0 || fileRecord.getBillerID().length() == 0 || 
/* 405 */       fileRecord.getBillCycle().length() == 0 || (
/* 406 */       fileRecord.getType().equals("02") && fileRecord.getSIB2000CA().length() == 0)) {
/* 407 */       log.error("ValidateFileRecord: Not all madatory fields are present");
/*     */       
/* 409 */       retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "8000", null));
/* 410 */       retInfo.setReturnCode("1");
/*     */ 
/*     */       
/*     */       return;
/*     */     } 
/*     */ 
/*     */     
/* 417 */     if (fileRecord.getType().equals("02")) {
/* 418 */       StringBuffer caBranch = new StringBuffer("");
/* 419 */       StringBuffer caType = new StringBuffer("");
/* 420 */       StringBuffer caNumber = new StringBuffer("");
/* 421 */       int field = 0;
/* 422 */       int index = 0;
/* 423 */       int fieldLen = fileRecord.getSIB2000CA().length();
/*     */       
/* 425 */       while (index < fieldLen) {
/* 426 */         if (fileRecord.getSIB2000CA().charAt(index) == '-') {
/* 427 */           field++;
/*     */         } else {
/* 429 */           switch (field) {
/*     */             case 0:
/* 431 */               caBranch.append(fileRecord.getSIB2000CA().charAt(index));
/*     */               break;
/*     */             case 1:
/* 434 */               caType.append(fileRecord.getSIB2000CA().charAt(index));
/*     */               break;
/*     */             case 2:
/* 437 */               caNumber.append(fileRecord.getSIB2000CA().charAt(index));
/*     */               break;
/*     */             default:
/* 440 */               log.debug("The field value= " + field);
/*     */               break;
/*     */           } 
/*     */         } 
/* 444 */         index++;
/*     */       } 
/*     */       
/* 447 */       if (caBranch.length() != 5 || caType.length() != 3 || caNumber.length() != 13) {
/* 448 */         log.error("ValidateFileRecord: The SIB2000 C/A fields length are not valid!");
/*     */         
/* 450 */         retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "8000", null));
/* 451 */         retInfo.setReturnCode("1");
/*     */         
/*     */         return;
/*     */       } 
/* 455 */       fileRecord.setCABranch(caBranch.toString());
/* 456 */       fileRecord.setCAType(caType.toString());
/* 457 */       fileRecord.setCANumber(caNumber.toString());
/*     */     } 
/*     */     
/* 460 */     retInfo.setReturnCode("0");
/*     */   }
/*     */ }


/* Location:              F:\SAMADecompileRBSCode\RbsEAR (1).zip!\RbsSTCBusiness.jar!\it\elsag\rbs\stc\business\STCFilesLocal.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */