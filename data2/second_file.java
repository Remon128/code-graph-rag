/*    */ package it.elsag.common.cif.business.delegate;
/*    */ 
/*    */ import it.elsag.common.cif.business.interfaces.ICIFBusiness;
/*    */ import it.elsag.common.cif.business.interfaces.ICIFDelegateFactory;
/*    */ import it.elsag.common.cif.business.interfaces.ICustomerManagement;
/*    */ import it.elsag.common.exceptions.ServiceException;
/*    */ 
/*    */ public class CIFDelegateSpringFactory
/*    */   implements ICIFDelegateFactory {
/*    */   private ICIFBusiness iCIFBusiness;
/*    */   private ICustomerManagement iCustomerManagement;
/*    */   
/*    */   public void setICIFBusiness(ICIFBusiness business) {
/* 14 */     this.iCIFBusiness = business;
/*    */   }
/*    */   
/*    */   public void setICustomerManagement(ICustomerManagement customerManagement) {
/* 18 */     this.iCustomerManagement = customerManagement;
/*    */   }
/*    */ 
/*    */   
/*    */   public ICIFBusiness createCIFBusiness() throws ServiceException {
/* 23 */     return this.iCIFBusiness;
/*    */   }
/*    */ 
/*    */ 
/*    */   
/*    */   public ICustomerManagement createCustomerManagement() throws ServiceException {
/* 29 */     return this.iCustomerManagement;
/*    */   }
/*    */ }


/* Location:              F:\SAMADecompileRBSCode\RbsEAR (1).zip!\CommonCIFCommon.jar!\it\elsag\common\cif\business\delegate\CIFDelegateSpringFactory.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */