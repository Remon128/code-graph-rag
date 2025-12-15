/*     */ package it.elsag.rbs.stc.business;
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
/*     */ 
/*     */ 
/*     */ public class STCFileRecord
/*     */ {
/*  16 */   private int m_iRecIndex = 0;
/*  17 */   private String m_strType = null;
/*  18 */   private String m_strAmount = null;
/*  19 */   private String m_strAccountNo = null;
/*  20 */   private String m_strChequeNo = null;
/*  21 */   private String m_strSIB2000CA = null;
/*  22 */   private String m_strCABranch = null;
/*  23 */   private String m_strCAType = null;
/*  24 */   private String m_strCANumber = null;
/*  25 */   private String m_strGovName = null;
/*  26 */   private String m_strDeptCode = null;
/*  27 */   private String m_strServiceType = null;
/*  28 */   private String m_strRecDate = null;
/*  29 */   private String m_strBillerID = null;
/*  30 */   private String m_strBillCycle = null;
/*  31 */   private String m_strFileRecord = null;
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public int getRecIndex() {
/*  37 */     return this.m_iRecIndex;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setRecIndex(int i) {
/*  44 */     this.m_iRecIndex = i;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getAmount() {
/*  51 */     return this.m_strAmount;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getAccountNo() {
/*  58 */     return this.m_strAccountNo;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getServiceType() {
/*  65 */     return this.m_strServiceType;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getChequeNo() {
/*  72 */     return this.m_strChequeNo;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getSIB2000CA() {
/*  79 */     return this.m_strSIB2000CA;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getCABranch() {
/*  86 */     return this.m_strCABranch;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getCAType() {
/*  93 */     return this.m_strCAType;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getCANumber() {
/* 100 */     return this.m_strCANumber;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getDeptCode() {
/* 107 */     return this.m_strDeptCode;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getFileRecord() {
/* 114 */     return this.m_strFileRecord;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getGovName() {
/* 121 */     return this.m_strGovName;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getType() {
/* 128 */     return this.m_strType;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setAmount(String string) {
/* 135 */     this.m_strAmount = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setAccountNo(String string) {
/* 142 */     this.m_strAccountNo = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setServiceType(String string) {
/* 149 */     this.m_strServiceType = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setChequeNo(String string) {
/* 156 */     this.m_strChequeNo = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setSIB2000CA(String string) {
/* 163 */     this.m_strSIB2000CA = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setCABranch(String string) {
/* 170 */     this.m_strCABranch = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setCAType(String string) {
/* 177 */     this.m_strCAType = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setCANumber(String string) {
/* 184 */     this.m_strCANumber = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setDeptCode(String string) {
/* 191 */     this.m_strDeptCode = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setFileRecord(String string) {
/* 198 */     this.m_strFileRecord = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setGovName(String string) {
/* 205 */     this.m_strGovName = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setType(String string) {
/* 212 */     this.m_strType = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getRecDate() {
/* 219 */     return this.m_strRecDate;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setRecDate(String string) {
/* 226 */     this.m_strRecDate = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getBillCycle() {
/* 233 */     return this.m_strBillCycle;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public String getBillerID() {
/* 240 */     return this.m_strBillerID;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setBillCycle(String string) {
/* 247 */     this.m_strBillCycle = string;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public void setBillerID(String string) {
/* 254 */     this.m_strBillerID = string;
/*     */   }
/*     */ }


/* Location:              F:\SAMADecompileRBSCode\RbsEAR (1).zip!\RbsSTCBusiness.jar!\it\elsag\rbs\stc\business\STCFileRecord.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */