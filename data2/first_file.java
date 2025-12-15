/*      */ package it.elsag.rbs.cheques.ejb.facade;
/*      */ 
/*      */ import it.elsag.common.ClassFactory;
/*      */ import it.elsag.common.CurrencyItem;
/*      */ import it.elsag.common.LogHelper;
/*      */ import it.elsag.common.Role;
/*      */ import it.elsag.common.accounting.data.AccountingData;
/*      */ import it.elsag.common.beans.MarketingInfoProcessBean;
/*      */ import it.elsag.common.beans.MarketingKeyBean;
/*      */ import it.elsag.common.business.MarketingLogic;
/*      */ import it.elsag.common.business.global.AuthUtil;
/*      */ import it.elsag.common.business.global.BankSetings;
/*      */ import it.elsag.common.business.global.CorrespondentBankData;
/*      */ import it.elsag.common.business.global.CurrencyData;
/*      */ import it.elsag.common.business.global.GlobalFunctions;
/*      */ import it.elsag.common.business.global.ProductSettingData;
/*      */ import it.elsag.common.business.global.RegionForLocalBankData;
/*      */ import it.elsag.common.business.interfaces.IAccountingBO;
/*      */ import it.elsag.common.business.interfaces.IAuthorization;
/*      */ import it.elsag.common.business.interfaces.IFrameworkDelegateFactory;
/*      */ import it.elsag.common.ca.business.CAFunctions;
/*      */ import it.elsag.common.ca.business.CAUtils;
/*      */ import it.elsag.common.config.ObjectFunctionConfig;
/*      */ import it.elsag.common.control.AuthzControlItem;
/*      */ import it.elsag.common.control.AuthzKeyItem;
/*      */ import it.elsag.common.control.CallerInfo;
/*      */ import it.elsag.common.control.ErrorItem;
/*      */ import it.elsag.common.control.ReturnInfo;
/*      */ import it.elsag.common.control.ValidationError;
/*      */ import it.elsag.common.desk.business.interfaces.IDeskBusiness;
/*      */ import it.elsag.common.desk.business.interfaces.IDeskDelegateFactory;
/*      */ import it.elsag.common.desk.ejb.facade.DeskController;
/*      */ import it.elsag.common.ejb.BaseSessionBean;
/*      */ import it.elsag.common.engine.orc.BricContainer;
/*      */ import it.elsag.common.exceptions.BusinessException;
/*      */ import it.elsag.common.exceptions.DeskException;
/*      */ import it.elsag.common.exceptions.ServiceException;
/*      */ import it.elsag.common.items.CorrespBanksItem;
/*      */ import it.elsag.common.items.ExchangeRateItem;
/*      */ import it.elsag.common.items.ProductSettingsItem;
/*      */ import it.elsag.common.items.TransactionCodeItem;
/*      */ import it.elsag.common.logging.business.LoggingLogic;
/*      */ import it.elsag.common.types.CARelation;
/*      */ import it.elsag.common.types.ElsagAmount;
/*      */ import it.elsag.common.types.ElsagDate;
/*      */ import it.elsag.common.utils.LabelValueBean;
/*      */ import it.elsag.common.utils.SessionBeanInterceptor;
/*      */ import it.elsag.common.utils.StringUtils;
/*      */ import it.elsag.common.valueobject.VODynaBean;
/*      */ import it.elsag.common.valueobject.VOFactory;
/*      */ import it.elsag.rbs.ca.business.CurrentAccountObject;
/*      */ import it.elsag.rbs.cheques.business.ChequeValueDateObject;
/*      */ import it.elsag.rbs.cheques.business.ChequesFunctions;
/*      */ import it.elsag.rbs.cheques.business.ChequesObject;
/*      */ import it.elsag.rbs.cheques.business.ChequesStatusObject;
/*      */ import it.elsag.rbs.cheques.business.ClearingChequeRejectionObject;
/*      */ import it.elsag.rbs.cheques.business.ForeignChequesObject;
/*      */ import it.elsag.rbs.cheques.business.MultipleChequesStatusObject;
/*      */ import it.elsag.rbs.cheques.business.ReversalClearingChequeObject;
/*      */ import it.elsag.rbs.cheques.business.interfaces.IChequesBusiness;
/*      */ import it.elsag.rbs.cheques.business.interfaces.IChequesBusinessRemote;
/*      */ import it.elsag.rbs.cheques.common.ChequesCABItem;
/*      */ import it.elsag.rbs.cheques.common.RejectClearingChequeConfigItem;
/*      */ import it.elsag.rbs.lp.business.LocalPaymentObject;
/*      */ import java.math.BigDecimal;
/*      */ import java.rmi.RemoteException;
/*      */ import java.sql.SQLException;
/*      */ import java.util.ArrayList;
/*      */ import java.util.Calendar;
/*      */ import java.util.Date;
/*      */ import java.util.Iterator;
/*      */ import java.util.LinkedHashMap;
/*      */ import java.util.Set;
/*      */ import javax.ejb.Handle;
/*      */ import javax.ejb.Local;
/*      */ import javax.ejb.Remote;
/*      */ import javax.ejb.Stateless;
/*      */ import javax.interceptor.Interceptors;
/*      */ import javax.sql.rowset.CachedRowSet;
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ @Stateless
/*      */ @Local({IChequesBusiness.class})
/*      */ @Remote({IChequesBusinessRemote.class})
/*      */ @Interceptors({SessionBeanInterceptor.class})
/*      */ public class ChequesControllerBean
/*      */   extends BaseSessionBean
/*      */   implements IChequesBusiness
/*      */ {
/*   97 */   private static LogHelper log = LogHelper.getInstance(ChequesControllerBean.class);
/*      */   
/*      */   public ReturnInfo getChequeTypeList(CallerInfo caller) throws ServiceException {
/*  100 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/*  101 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/*  102 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/*  104 */     StringBuffer sql = new StringBuffer("SELECT C_TP_ASS, DZ");
/*  105 */     sql.append(" FROM ICSR1E05 INNER JOIN ICSR1I02 ON ICSR1E05.C_IST = ICSR1I02.C_IST AND ICSR1E05.ID_DZ = ICSR1I02.ID_DZ");
/*  106 */     sql.append(" WHERE ICSR1I02.C_LNG = '");
/*  107 */     sql.append(caller.getLanguage()).append("'");
/*  108 */     sql.append(" AND ICSR1E05.C_IST = $(BANK)");
/*      */     
/*  110 */     sql.append(" ORDER BY C_TP_ASS ASC");
/*      */ 
/*      */     
/*  113 */     LinkedHashMap<Object, Object> chequeTypeList = new LinkedHashMap<Object, Object>();
/*      */     try {
/*  115 */       CachedRowSet crs = SQLSelect(caller, sql.toString(), new Object[0]);
/*  116 */       if (crs != null) {
/*  117 */         while (crs.next()) {
/*  118 */           String value = crs.getString("C_TP_ASS");
/*  119 */           StringBuffer label = new StringBuffer(value);
/*  120 */           label.append(" - ").append(crs.getString("DZ"));
/*  121 */           LabelValueBean lvb = new LabelValueBean(label.toString(), value);
/*  122 */           chequeTypeList.put(value, lvb);
/*      */         } 
/*      */       }
/*  125 */     } catch (ServiceException e) {
/*  126 */       throw new ServiceException("Error getChequeTypeList(): ", e);
/*  127 */     } catch (SQLException e) {
/*  128 */       throw new ServiceException("Error getChequeTypeList(): ", e);
/*      */     } 
/*  130 */     if (chequeTypeList == null || chequeTypeList.isEmpty())
/*  131 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1050", null)); 
/*  132 */     retInfo.setOutputParameter("RETURNLIST", chequeTypeList);
/*  133 */     retInfo.setOK();
/*  134 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo getCABList(CallerInfo caller) throws ServiceException {
/*  138 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/*  139 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/*  140 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/*  142 */     StringBuffer sql = new StringBuffer("SELECT C_AG, C_CAB, DZ");
/*  143 */     sql.append(" FROM ICSR1C02 INNER JOIN ICSR1I02 ON ICSR1C02.C_IST = ICSR1I02.C_IST AND ICSR1C02.ID_DZ = ICSR1I02.ID_DZ");
/*  144 */     sql.append(" WHERE ICSR1I02.C_LNG = '");
/*  145 */     sql.append(caller.getLanguage()).append("'");
/*  146 */     sql.append(" AND ICSR1C02.C_IST = $(BANK)");
/*  147 */     sql.append(" AND F_SIB2000 = '1'");
/*  148 */     sql.append(" AND ICSR1C02.DT_SCAD_VALD IS NULL");
/*  149 */     sql.append(" ORDER BY C_CAB");
/*      */     
/*  151 */     LinkedHashMap<Object, Object> cabList = new LinkedHashMap<Object, Object>();
/*      */     try {
/*  153 */       CachedRowSet crs = SQLSelect(caller, sql.toString(), new Object[0]);
/*  154 */       if (crs != null) {
/*  155 */         while (crs.next()) {
/*  156 */           String cab = crs.getString("C_CAB");
/*  157 */           StringBuffer label = new StringBuffer(cab);
/*  158 */           label.append(" - ").append(crs.getString("DZ"));
/*  159 */           ChequesCABItem cabItem = new ChequesCABItem();
/*  160 */           cabItem.setCab(cab);
/*  161 */           cabItem.setBranch(crs.getString("C_AG"));
/*  162 */           cabItem.setDescription(label.toString());
/*  163 */           cabList.put(cab, cabItem);
/*      */         } 
/*      */       }
/*  166 */     } catch (ServiceException e) {
/*  167 */       throw new ServiceException("Error getCABList(): ", e);
/*  168 */     } catch (SQLException e) {
/*  169 */       throw new ServiceException("Error getCABList(): ", e);
/*      */     } 
/*  171 */     if (cabList.isEmpty())
/*  172 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1050", null)); 
/*  173 */     retInfo.setOutputParameter("RETURNLIST", cabList);
/*  174 */     retInfo.setOK();
/*  175 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo getRegionListForLocalBank(CallerInfo caller) throws ServiceException {
/*  179 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/*  180 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/*  181 */     ReturnInfo retInfo = new ReturnInfo();
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/*  191 */     ArrayList<LabelValueBean> regionForLocalBankList = new ArrayList();
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/*  208 */     LinkedHashMap regionMap = RegionForLocalBankData.getRegionForLocalBankList(caller);
/*  209 */     if (regionMap != null) {
/*  210 */       Iterator<String> regionMapIter = regionMap.keySet().iterator();
/*  211 */       while (regionMapIter.hasNext()) {
/*  212 */         String region = regionMapIter.next();
/*  213 */         LabelValueBean lvb = (LabelValueBean)regionMap.get(region);
/*  214 */         LabelValueBean newLvb = new LabelValueBean(String.valueOf(region) + " - " + lvb.getLabel(), region);
/*  215 */         regionForLocalBankList.add(newLvb);
/*      */       } 
/*      */     } 
/*      */     
/*  219 */     if (regionForLocalBankList.isEmpty())
/*  220 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1050", null)); 
/*  221 */     retInfo.setOutputParameter("RETURNLIST", regionForLocalBankList);
/*  222 */     retInfo.setOK();
/*  223 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo getAvailabilityDaysForCABType(CallerInfo caller, String cabType) throws ServiceException {
/*  227 */     return ChequesFunctions.getAvailabilityDaysForCABType(caller, cabType);
/*      */   }
/*      */   
/*      */   public ReturnInfo ChequeDebit(CallerInfo caller, Handle deskHandle, ArrayList<VODynaBean> VOList, ArrayList<ExchangeRateItem> exchangeItemList, int deskIndexBO) throws ServiceException {
/*  231 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/*  232 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/*  233 */     if (VOList == null || (VOList.size() > 1 && deskIndexBO != -1)) {
/*  234 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/*      */ 
/*      */     
/*  238 */     Calendar cal = Calendar.getInstance();
/*  239 */     cal.add(5, 1);
/*  240 */     ElsagDate tomorrow = new ElsagDate(cal.getTime());
/*  241 */     ElsagDate today = new ElsagDate(new Date());
/*  242 */     ElsagDate accountingDate = new ElsagDate(caller.getAccountingDate());
/*  243 */     if (accountingDate.compareTo(today) != 0 && accountingDate.compareTo(tomorrow) != 0) {
/*  244 */       ValidationError err = new ValidationError();
/*  245 */       err.setValErrorKeyMessage("message.error.validation.accountingdatenotequaltosolardate");
/*  246 */       return (new ReturnInfo()).addValidationError(err);
/*      */     } 
/*      */ 
/*      */ 
/*      */     
/*  251 */     ReturnInfo retInfo = new ReturnInfo();
/*  252 */     LinkedHashMap<Object, Object> lhm = new LinkedHashMap<Object, Object>();
/*  253 */     DeskController deskController = null;
/*      */     
/*      */     try {
/*  256 */       deskController = (DeskController)deskHandle.getEJBObject();
/*  257 */       if (deskController == null)
/*  258 */         throw new ServiceException("Error: DeskController is Null"); 
/*  259 */     } catch (RemoteException e) {
/*  260 */       throw new ServiceException("Error: ChequeDebit() ", e);
/*      */     } 
/*      */     
/*  263 */     if (exchangeItemList != null)
/*  264 */       for (int i = 0; i < exchangeItemList.size(); i++) {
/*  265 */         ExchangeRateItem eri = exchangeItemList.get(i);
/*  266 */         if (!StringUtils.isEmpty(eri.getCurrency()))
/*  267 */           lhm.put(eri.getCurrency(), eri); 
/*      */       }  
/*  269 */     Set currencies = lhm.keySet();
/*      */     
/*  271 */     if (VOList != null)
/*  272 */       for (int i = 0; i < VOList.size(); i++) {
/*  273 */         VODynaBean bean = VOList.get(i);
/*  274 */         String currency = (String)bean.get("Currency");
/*  275 */         ChequesObject chequesObject = new ChequesObject(Role.Debit);
/*      */         try {
/*  277 */           chequesObject.setCallerInfo(caller);
/*      */           
/*  279 */           String functionCode = BankSetings.getAbi().equals(bean.get("Bank")) ? "139" : "149";
/*  280 */           ExchangeRateItem eri = null;
/*  281 */           if (currencies.contains(currency)) {
/*  282 */             eri = (ExchangeRateItem)lhm.get(currency);
/*      */           }
/*  284 */           if (bean.get("ExchangeRateItem") != null && ((ExchangeRateItem)bean.get("ExchangeRateItem")).getEtLcyAmount() != null) {
/*  285 */             eri = (ExchangeRateItem)bean.get("ExchangeRateItem");
/*      */           }
/*  287 */           retInfo = chequesObject.setData(bean, functionCode, eri);
/*      */           
/*  289 */           if (retInfo.isError()) return retInfo;
/*      */           
/*  291 */           BigDecimal chequeNo = (BigDecimal)bean.get("ChequeNumber");
/*  292 */           String sAuthID = (String)caller.getAuthzKey(new AuthzKeyItem("SB20", "06", "22", chequeNo.toString()));
/*  293 */           if (sAuthID != null) {
/*  294 */             chequesObject.addAuthID(sAuthID);
/*      */           }
/*  296 */           String sAuthID2 = (String)caller.getAuthzKey(new AuthzKeyItem("SB20", "06", "23", chequeNo.toString()));
/*  297 */           if (sAuthID2 != null) {
/*  298 */             chequesObject.addAuthID(sAuthID2);
/*      */           }
/*      */         }
/*  301 */         catch (BusinessException e) {
/*  302 */           throw new ServiceException("Error: ChequeDebit() ", e);
/*      */         } 
/*      */         
/*      */         try {
/*  306 */           retInfo = deskController.addBusinessObject((IAccountingBO)chequesObject, deskIndexBO);
/*  307 */         } catch (DeskException e) {
/*  308 */           throw new ServiceException("Error: addBusinessObject() ", e);
/*  309 */         } catch (RemoteException e) {
/*  310 */           throw new ServiceException("Error: addBusinessObject() ", e);
/*      */         } 
/*      */       }  
/*  313 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo getChequesStatusTypeList(CallerInfo caller, Boolean mode) throws ServiceException {
/*  317 */     return ChequesFunctions.GetChequesStatusTypeList(caller, mode);
/*      */   }
/*      */   
/*      */   public ReturnInfo getInitialStatusTypeList(CallerInfo caller, String finalStatus) throws ServiceException {
/*  321 */     return ChequesFunctions.GetInitialStatusTypeList(caller, finalStatus);
/*      */   }
/*      */ 
/*      */   
/*      */   public ReturnInfo ChangeStatus(CallerInfo caller, Handle deskHandle, VODynaBean VO, int deskIndexBO, boolean multiple) throws ServiceException {
/*      */     MultipleChequesStatusObject multipleChequesStatusObject;
/*  327 */     ReturnInfo ret = new ReturnInfo();
/*      */     
/*  329 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/*  330 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*      */ 
/*      */     
/*  334 */     if (VO == null || (!multiple && !VO.getDynaClass().getName().equals("ChequesbookChangeStatus")) || (
/*  335 */       multiple && !VO.getDynaClass().getName().equals("MultipleChequesbookChangeStatus"))) {
/*      */       
/*  337 */       ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*  338 */       ret.setReturnCode("1");
/*  339 */       return ret;
/*      */     } 
/*      */     
/*  342 */     ArrayList validationErrors = VO.validate();
/*  343 */     if (!validationErrors.isEmpty()) {
/*  344 */       ret.setValidationErrors(validationErrors);
/*  345 */       ret.setReturnCode("1");
/*  346 */       return ret;
/*      */     } 
/*      */ 
/*      */     
/*  350 */     AuthzKeyItem itemAuthKey = new AuthzKeyItem("SB20", "06", "8", "");
/*  351 */     ReturnInfo retAuth = AuthUtil.VerifyAuthorizationNeed(caller, itemAuthKey);
/*  352 */     String sAuthID = (String)retAuth.getOutputParameter("OBJECTAUTHID");
/*      */     
/*  354 */     if (retAuth.isError()) return retAuth;
/*      */     
/*  356 */     ChequesStatusObject chequesStatusObj = new ChequesStatusObject();
/*      */     
/*  358 */     if (multiple) {
/*  359 */       multipleChequesStatusObject = new MultipleChequesStatusObject();
/*      */     }
/*      */     
/*  362 */     if (!StringUtils.isEmpty(sAuthID)) {
/*  363 */       multipleChequesStatusObject.addAuthID(sAuthID);
/*      */     }
/*      */     try {
/*  366 */       multipleChequesStatusObject.setCallerInfo(caller);
/*  367 */       ret = multipleChequesStatusObject.setData(VO, caller.getFunction(), null);
/*  368 */       if (ret.isError()) {
/*  369 */         return ret;
/*      */       }
/*      */     }
/*  372 */     catch (BusinessException e) {
/*  373 */       throw new ServiceException("Error: ChangeDraftStatus() ", e);
/*      */     } 
/*      */ 
/*      */     
/*  377 */     MarketingKeyBean marketingKeyBean = new MarketingKeyBean();
/*  378 */     marketingKeyBean.setBranch(caller.getBranch());
/*  379 */     marketingKeyBean.setFunctionId(caller.getFunction());
/*  380 */     marketingKeyBean.setCIC((String)VO.get("CIC"));
/*  381 */     marketingKeyBean.setJuridicalStatus((String)VO.get("JuridicalStatus"));
/*  382 */     multipleChequesStatusObject.setMarketingKeyBean(marketingKeyBean);
/*      */     
/*  384 */     double amount = multipleChequesStatusObject.getAmount();
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/*  390 */     if (amount == 0.0D) {
/*      */ 
/*      */       
/*  393 */       MarketingInfoProcessBean marketingBean = (MarketingInfoProcessBean)VO.get("MarketingBean");
/*  394 */       if (StringUtils.isEmpty(marketingBean.getFunctionId()) && GlobalFunctions.isAuthorizedSignatoryRequired(caller, marketingKeyBean)) {
/*  395 */         ret.addError(new ErrorItem("0", "SRV_BUSINESS", "ASREQ", null));
/*  396 */         return ret;
/*      */       } 
/*      */       
/*  399 */       BricContainer bricContainer = new BricContainer();
/*  400 */       ArrayList printList = null;
/*      */       try {
/*  402 */         bricContainer.AddLinkedHashMap(multipleChequesStatusObject.PrepareData("", 0.0D, -1.0D, 0.0D, 0.0D));
/*      */       }
/*  404 */       catch (BusinessException e) {
/*  405 */         throw new ServiceException(" Error: Release() ", e);
/*      */       } 
/*      */       
/*  408 */       ret = MarketingLogic.prepareMarketingInfoLegacy(caller, marketingBean);
/*  409 */       if (ret.isError())
/*  410 */         return ret; 
/*  411 */       bricContainer.AddLinkedHashMap((LinkedHashMap)ret.removeOutputParameter("CONTAINER_OUT"));
/*      */       
/*  413 */       if (!bricContainer.ExecTransaction(caller)) {
/*  414 */         return bricContainer.getReturnInfo();
/*      */       }
/*      */       
/*  417 */       ProductSettingsItem prodInfo = ProductSettingData.getProductSettingsInfo(caller.getProdCode());
/*  418 */       if (!caller.isExternal() && !prodInfo.isFlagHostOnlySess()) {
/*      */         
/*  420 */         String keyIn = ObjectFunctionConfig.getObjectFunctionIN("ChequesChangeStatus");
/*      */         
/*  422 */         LinkedHashMap<String, ArrayList<LinkedHashMap<String, Object>>> rsSendList = new LinkedHashMap<String, ArrayList<LinkedHashMap<String, Object>>>();
/*  423 */         String formatedObjFuncKey = BricContainer.FormatObjFuncKey(keyIn);
/*  424 */         LinkedHashMap<String, Object> element = new LinkedHashMap<String, Object>();
/*  425 */         ArrayList<LinkedHashMap<String, Object>> arr = new ArrayList<LinkedHashMap<String, Object>>();
/*      */         
/*  427 */         CachedRowSet crs_input = bricContainer.getInputRowSet(keyIn);
/*      */         try {
/*  429 */           if (crs_input != null) {
/*  430 */             crs_input.beforeFirst();
/*  431 */             while (crs_input.next()) {
/*  432 */               element = new LinkedHashMap<String, Object>();
/*      */               
/*  434 */               String cAg = crs_input.getString("C_AG");
/*  435 */               String cTpRapp = crs_input.getString("C_TP_RAPP");
/*  436 */               String nRapp = crs_input.getString("N_RAPP");
/*  437 */               CARelation caRelation = new CARelation();
/*  438 */               caRelation.setRELATIONBRANCH(cAg);
/*  439 */               caRelation.setRELATIONTYPE(cTpRapp);
/*  440 */               caRelation.setRELATIONNUMBER(nRapp);
/*      */               
/*  442 */               ReturnInfo ret_commCA = CAFunctions.getCAInfo(caller, caRelation);
/*      */               
/*  444 */               String subheading = "";
/*  445 */               if (ret_commCA != null && !ret_commCA.isError()) {
/*  446 */                 VODynaBean CADetails = (VODynaBean)ret_commCA.getOutputParameter("CAInfoBean");
/*  447 */                 if (CADetails != null) {
/*  448 */                   VODynaBean detailsVO = (VODynaBean)CADetails.get("DetailsVO");
/*  449 */                   subheading = (String)detailsVO.get("Subheading");
/*  450 */                   element.put("SHORTHEADING", subheading);
/*      */                 } 
/*      */               } 
/*      */               
/*  454 */               Date d = new Date();
/*  455 */               String time = String.valueOf(String.format("%02d", new Object[] { Integer.valueOf(d.getHours()) })) + ":" + String.format("%02d", new Object[] { Integer.valueOf(d.getMinutes()) });
/*  456 */               element.put("TIME", time);
/*  457 */               element.put("DT_VAL", (new ElsagDate(d)).toString());
/*  458 */               element.put("OPERATOR", caller.getUser());
/*  459 */               arr.add(element);
/*      */             } 
/*  461 */             rsSendList.put(formatedObjFuncKey, arr);
/*  462 */             LoggingLogic.InsertMoreLog(caller.getBranch(), new BigDecimal(caller.getSessionID().intValue()), caller.getOperationNo().intValue() + 1, rsSendList);
/*      */           } 
/*  464 */         } catch (SQLException e) {
/*  465 */           log.error("Error read more info log ChangeStatus : ", e);
/*      */         } 
/*      */       } 
/*      */ 
/*      */       
/*  470 */       ret = bricContainer.getReturnInfo();
/*      */ 
/*      */       
/*      */       try {
/*  474 */         printList = multipleChequesStatusObject.print(caller, null);
/*  475 */       } catch (BusinessException be) {
/*  476 */         throw new ServiceException("Error ChangeStatus()", be);
/*      */       } 
/*      */       
/*  479 */       if (printList != null) {
/*  480 */         ret.setOutputParameter("PRINT_LIST", printList);
/*      */       }
/*      */     } else {
/*      */       
/*      */       try {
/*  485 */         DeskController deskController = (DeskController)deskHandle.getEJBObject();
/*  486 */         if (deskController == null) {
/*  487 */           throw new ServiceException("Error: DeskController is Null");
/*      */         }
/*  489 */         CurrentAccountObject caObject = null;
/*  490 */         if (deskIndexBO == -1 && amount != 0.0D) {
/*  491 */           VODynaBean caWorkInfoVO = multipleChequesStatusObject.getWorkInfo();
/*  492 */           if (caWorkInfoVO != null) {
/*  493 */             VODynaBean VOTransfer = VOFactory.create("CurrAccTransferVO");
/*  494 */             VOTransfer.set("ValueDate", new ElsagDate(new Date()));
/*  495 */             VOTransfer.set("Remark", "");
/*  496 */             VOTransfer.set("CAWorkInfo", caWorkInfoVO);
/*      */             
/*  498 */             caObject = new CurrentAccountObject(Role.Debit);
/*      */             try {
/*  500 */               caObject.setCallerInfo(caller);
/*  501 */               ret = caObject.setData(VOTransfer, "258", null);
/*  502 */               if (ret.isError()) {
/*  503 */                 return ret;
/*      */               }
/*  505 */             } catch (BusinessException e) {
/*  506 */               throw new ServiceException("Error: paymentForeignCheques() ", e);
/*      */             } 
/*      */           } 
/*      */         } 
/*  510 */         ret = deskController.addBusinessObject((IAccountingBO)multipleChequesStatusObject, deskIndexBO);
/*  511 */         if (ret.isError())
/*  512 */           return ret; 
/*  513 */         if (caObject != null) {
/*  514 */           ret = deskController.addBusinessObject((IAccountingBO)caObject, -1);
/*      */         }
/*  516 */         ret.setOutputParameter("SAVEDTODESK", "1");
/*      */       
/*      */       }
/*  519 */       catch (DeskException e) {
/*  520 */         throw new ServiceException("Error: addBusinessObject() ", e);
/*  521 */       } catch (RemoteException e) {
/*  522 */         throw new ServiceException("Error: addBusinessObject() ", e);
/*      */       } 
/*      */     } 
/*      */ 
/*      */     
/*  527 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo SearchChequeDetails(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/*  533 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/*  534 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*      */     
/*  537 */     String searchVOName = "ChequesSearchFormBean";
/*  538 */     if (searchVO == null || !searchVO.getDynaClass().getName().equals(searchVOName))
/*  539 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null)); 
/*  540 */     String returnVOName = "ChequesDetailsFormBean";
/*      */     
/*  542 */     BricContainer container = new BricContainer();
/*  543 */     CachedRowSet crs = null;
/*      */     
/*  545 */     BigDecimal number = (BigDecimal)searchVO.get("Number");
/*      */     
/*  547 */     if (number != null && !number.equals(new BigDecimal(0.0D))) {
/*  548 */       String searchBricKey1 = "CRCHQ.001";
/*  549 */       crs = container.createRowSet(searchBricKey1, "0002");
/*      */       try {
/*  551 */         CARelation caRelation = (CARelation)searchVO.get("CARelation");
/*  552 */         crs.moveToInsertRow();
/*  553 */         crs.updateString("C_AG", caRelation.getRELATIONBRANCH());
/*  554 */         crs.updateString("C_TP_RAPP", caRelation.RELATIONTYPE);
/*  555 */         crs.updateString("N_RAPP", caRelation.RELATIONNUMBER);
/*  556 */         crs.updateBigDecimal("N", (BigDecimal)searchVO.get("Number"));
/*  557 */         crs.insertRow();
/*  558 */       } catch (SQLException e) {
/*  559 */         throw new ServiceException("Error SQL  SearchChequeDetails():" + e.getMessage(), "9000");
/*      */       } 
/*      */     } else {
/*      */       
/*  563 */       String searchBricKey2 = "CRCHQ.002";
/*  564 */       crs = container.createRowSet(searchBricKey2, "0002");
/*      */       try {
/*  566 */         crs.moveToInsertRow();
/*  567 */         crs.updateObject("C_ABI", searchVO.get("Bank"));
/*  568 */         crs.updateObject("C_CAB", searchVO.get("Region"));
/*  569 */         crs.updateObject("N_CT_TRAE", StringUtils.addInitialZeroDigits((String)searchVO.get("CANumber"), 8));
/*  570 */         crs.updateObject("TP", searchVO.get("Type"));
/*  571 */         crs.updateBigDecimal("N", (BigDecimal)searchVO.get("ChequeNumber"));
/*  572 */         crs.insertRow();
/*  573 */       } catch (SQLException e) {
/*  574 */         throw new ServiceException("Error SQL  SearchChequeDetails():" + e.getMessage(), "9000");
/*      */       } 
/*      */     } 
/*      */     
/*  578 */     if (!container.ExecuteQuery(caller)) {
/*  579 */       return container.getReturnInfo();
/*      */     }
/*  581 */     if (number != null && !number.equals(new BigDecimal(0.0D))) {
/*  582 */       String returnBricKey1 = "CRCHQ.501";
/*  583 */       if (!container.TransferToVO(returnBricKey1, returnVOName))
/*  584 */         return container.getReturnInfo(); 
/*      */     } else {
/*  586 */       String returnBricKey2 = "CRCHQ.501";
/*  587 */       if (!container.TransferToVO(returnBricKey2, returnVOName)) {
/*  588 */         return container.getReturnInfo();
/*      */       }
/*      */     } 
/*  591 */     VODynaBean detailVO = container.getReturnVO(returnVOName);
/*      */     
/*  593 */     ReturnInfo ret = new ReturnInfo();
/*  594 */     ret.setOutputParameter("RETURNVO", detailVO);
/*  595 */     ret.setOK();
/*  596 */     return ret;
/*      */   }
/*      */   
/*      */   public ReturnInfo verifyCheckID(CallerInfo caller, VODynaBean VO) throws ServiceException {
/*  600 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/*  601 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*  603 */     String searchBricKey = "BCHQ.008";
/*  604 */     String returnBricKey = "BCHQ.508";
/*      */     
/*  606 */     if (VO == null || !VO.getDynaClass().getName().equals("ChequesBankFormBean")) {
/*  607 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/*  609 */     BricContainer container = new BricContainer();
/*  610 */     CachedRowSet crs = null;
/*      */     
/*  612 */     crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/*  614 */       String caBranch = (String)VO.get("Region");
/*  615 */       String caType = (String)VO.get("CAType");
/*  616 */       String caNumber = StringUtils.addInitialZeroDigits((String)VO.get("CANumber"), 13);
/*  617 */       CARelation cheqCA = new CARelation(caBranch, caType, caNumber);
/*      */       
/*  619 */       crs.moveToInsertRow();
/*  620 */       crs.updateBigDecimal("N", (BigDecimal)VO.get("ChequeNumber"));
/*  621 */       crs.updateString("C_AG", cheqCA.getRELATIONBRANCH());
/*  622 */       crs.updateString("C_TP_RAPP", cheqCA.RELATIONTYPE);
/*  623 */       crs.updateString("N_RAPP", cheqCA.RELATIONNUMBER);
/*  624 */       crs.updateString("CHECK_ID", (String)VO.get("CheckID"));
/*  625 */       crs.insertRow();
/*  626 */     } catch (SQLException e) {
/*  627 */       throw new ServiceException("Error SQL  verifyCheckID():" + e.getMessage(), "9000");
/*      */     } 
/*  629 */     if (!container.ExecuteQuery(caller)) {
/*  630 */       return container.getReturnInfo();
/*      */     }
/*  632 */     Boolean checkIDValid = new Boolean(false);
/*  633 */     crs = container.getOutputRowSet(returnBricKey);
/*  634 */     if (crs != null) {
/*      */       try {
/*  636 */         crs.beforeFirst();
/*  637 */         if (crs.next()) {
/*  638 */           String status = crs.getString("C_ST");
/*  639 */           if ("1".equals(status))
/*  640 */             checkIDValid = new Boolean(true); 
/*      */         } 
/*  642 */       } catch (SQLException e) {
/*  643 */         throw new ServiceException("Error extracting data", e);
/*      */       } 
/*      */     }
/*  646 */     if (!checkIDValid.booleanValue()) {
/*  647 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Check ID"));
/*      */     }
/*  649 */     ReturnInfo ret = new ReturnInfo();
/*  650 */     ret.setOutputParameter("RETURNINFO", checkIDValid);
/*  651 */     ret.setOK();
/*  652 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo insertRejectedCheque(CallerInfo caller, VODynaBean rejectedCheque, ArrayList<LabelValueBean> rejectReasons) throws ServiceException {
/*  658 */     ReturnInfo ret = new ReturnInfo();
/*  659 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/*  660 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*  662 */     if (rejectedCheque == null || !rejectedCheque.getDynaClass().getName().equals("RejectedCheque")) {
/*  663 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "insertRejectedCheque expecting VO RejectedCheque"));
/*      */     }
/*  665 */     BricContainer container = new BricContainer();
/*      */     
/*  667 */     CachedRowSet crs = container.createRowSet("BCHQ.014", "0002");
/*      */     try {
/*  669 */       CARelation caRel = (CARelation)rejectedCheque.get("CARelation");
/*      */       
/*  671 */       crs.moveToInsertRow();
/*  672 */       crs.updateString("BRANCH", caRel.getRELATIONBRANCH());
/*  673 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/*  674 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/*  675 */       crs.updateBigDecimal("N_CHEQ", (BigDecimal)rejectedCheque.get("ChequeNumber"));
/*      */       
/*  677 */       crs.updateString("C_DV_MOV", (String)rejectedCheque.get("CurrencyCode"));
/*  678 */       crs.updateObject("IMP", ((ElsagAmount)rejectedCheque.get("Amount")).get4EIS());
/*  679 */       crs.updateObject("DT_EMIS", ((ElsagDate)rejectedCheque.get("IssueDate")).get4EIS());
/*  680 */       crs.updateString("NT", (String)rejectedCheque.get("Notes"));
/*      */       
/*  682 */       crs.updateString("NOM_I_BNFC", (String)rejectedCheque.get("NameFirstBenef"));
/*  683 */       crs.updateString("NOM_ULT_BNFC", (String)rejectedCheque.get("NameLastBenef"));
/*  684 */       crs.updateString("NOM_AGNT", (String)rejectedCheque.get("AgentName"));
/*  685 */       crs.updateString("ALTR_INFO", (String)rejectedCheque.get("OtherData"));
/*  686 */       crs.updateString("C_CAB", (String)rejectedCheque.get("RegionCode"));
/*  687 */       crs.insertRow();
/*      */       
/*  689 */       if (rejectReasons != null && !rejectReasons.isEmpty()) {
/*  690 */         CachedRowSet crs2 = container.createRowSet("BCHQ.015", "0002");
/*  691 */         for (int i = 0; i < rejectReasons.size(); i++) {
/*  692 */           crs2.moveToInsertRow();
/*  693 */           crs2.updateString("C_AG", caRel.getRELATIONBRANCH());
/*  694 */           crs2.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/*  695 */           crs2.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/*  696 */           crs2.updateBigDecimal("N_ASS", (BigDecimal)rejectedCheque.get("ChequeNumber"));
/*  697 */           crs2.updateString("C_MOT_RIFIU", ((LabelValueBean)rejectReasons.get(i)).getValue());
/*  698 */           crs2.insertRow();
/*      */         }
/*      */       
/*      */       } 
/*  702 */     } catch (SQLException e) {
/*  703 */       throw new ServiceException("Error SQL  insertRejectedCheque(): " + e.getMessage(), "9000");
/*      */     } 
/*      */ 
/*      */     
/*  707 */     ret = prepareInsertChequeImageID(caller, rejectedCheque);
/*  708 */     if (ret.isError())
/*  709 */       return ret; 
/*  710 */     container.AddLinkedHashMap((LinkedHashMap)ret.removeOutputParameter("CONTAINER_OUT"));
/*      */     
/*  712 */     if (!container.ExecTransaction(caller)) {
/*  713 */       return container.getReturnInfo();
/*      */     }
/*      */     
/*  716 */     ret = container.getReturnInfo();
/*      */     
/*  718 */     CachedRowSet crsOut = container.getOutputRowSet("BCHQ.514");
/*  719 */     if (crsOut == null) {
/*  720 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "9000", null));
/*      */     }
/*      */     try {
/*  723 */       crsOut.moveToCurrentRow();
/*  724 */       crsOut.beforeFirst();
/*  725 */       if (crsOut.next()) {
/*  726 */         ret.setOutputParameter("SEQUENCENUMBER", crsOut.getBigDecimal("SEQUENCENUMBER"));
/*      */       }
/*  728 */     } catch (SQLException e) {
/*  729 */       throw new ServiceException("Error insertRejectedCheque(): ", e);
/*      */     } 
/*      */     
/*  732 */     return ret;
/*      */   }
/*      */ 
/*      */   
/*      */   public ReturnInfo insertPrintedSlip(CallerInfo caller, VODynaBean printedSlip) throws ServiceException {
/*  737 */     ReturnInfo ret = new ReturnInfo();
/*  738 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/*  739 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*  741 */     if (printedSlip == null || !printedSlip.getDynaClass().getName().equals("SlipDetails")) {
/*  742 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "getDocumentImage expecting VO CustomerIDDocument"));
/*      */     }
/*  744 */     BricContainer container = new BricContainer();
/*  745 */     CachedRowSet crs = null;
/*      */     
/*  747 */     crs = container.createRowSet("BCHQ.011", "0002");
/*      */     try {
/*  749 */       CARelation caRel = (CARelation)printedSlip.get("CARelation");
/*      */       
/*  751 */       crs.moveToInsertRow();
/*  752 */       crs.updateString("CIC", (String)printedSlip.get("CIC"));
/*  753 */       crs.updateString("BRANCH", caRel.getRELATIONBRANCH());
/*  754 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/*  755 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/*  756 */       crs.updateBigDecimal("N_CHEQ", (BigDecimal)printedSlip.get("ChequeNumber"));
/*  757 */       crs.updateBigDecimal("SEQUENCENUMBER", (BigDecimal)printedSlip.get("SequenceNumber"));
/*  758 */       crs.insertRow();
/*  759 */     } catch (SQLException e) {
/*  760 */       throw new ServiceException("Error SQL  insertPrintedSlip(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/*  763 */     if (!container.ExecTransaction(caller)) {
/*  764 */       return container.getReturnInfo();
/*      */     }
/*      */     
/*  767 */     ret = container.getReturnInfo();
/*  768 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo searchNumberOfRejectedCheques(CallerInfo caller, ElsagDate from, ElsagDate to, String cic, CARelation caRel) throws ServiceException {
/*  774 */     return ChequesFunctions.searchNumberOfRejectedCheques(caller, from, to, cic, caRel);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getSlipDetails(CallerInfo caller, VODynaBean slip) throws ServiceException {
/*  826 */     ReturnInfo ret = new ReturnInfo();
/*  827 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/*  828 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*  830 */     if (slip == null || !slip.getDynaClass().getName().equals("SlipDetails"))
/*      */     {
/*  832 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "getSlipDetails expecting VO SlipDetails"));
/*      */     }
/*  834 */     String searchBricKey = "BCHQ.054";
/*  835 */     String returnBricKey = "BCHQ.554";
/*      */     
/*  837 */     BricContainer container = new BricContainer();
/*  838 */     CachedRowSet crs = null;
/*      */     
/*  840 */     crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/*  842 */       CARelation caRel = (CARelation)slip.get("CARelation");
/*      */       
/*  844 */       crs.moveToInsertRow();
/*  845 */       crs.updateString("CIC", (String)slip.get("CIC"));
/*  846 */       crs.updateString("BRANCH", caRel.getRELATIONBRANCH());
/*  847 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/*  848 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/*  849 */       crs.updateString("DT_FROM", ((ElsagDate)slip.get("DateFrom")).getValue());
/*  850 */       crs.updateString("DT_TO", ((ElsagDate)slip.get("DateTo")).getValue());
/*  851 */       crs.updateString("F_STMP", (String)slip.get("SlipPrintedFlag"));
/*  852 */       crs.insertRow();
/*  853 */     } catch (SQLException e) {
/*  854 */       throw new ServiceException("Error SQL  getSlipDetails(): " + e.getMessage(), "9000");
/*      */     } 
/*  856 */     if (!container.ExecuteQuery(caller)) {
/*  857 */       return container.getReturnInfo();
/*      */     }
/*      */     
/*  860 */     if (!container.TransferToVOList(returnBricKey, "SlipDetails")) {
/*  861 */       return container.getReturnInfo();
/*      */     }
/*      */     
/*  864 */     ArrayList<VODynaBean> list = container.getReturnVOList("SlipDetails");
/*      */     
/*  866 */     if (list != null) {
/*  867 */       ReturnInfo retInfo = getCABList(caller);
/*  868 */       if (retInfo.isError())
/*  869 */         return retInfo; 
/*  870 */       LinkedHashMap<Object, Object> cabList = (LinkedHashMap)retInfo.removeOutputParameter("RETURNLIST");
/*  871 */       if (cabList == null)
/*  872 */         cabList = new LinkedHashMap<Object, Object>(); 
/*  873 */       for (int i = 0; i < list.size(); i++) {
/*  874 */         VODynaBean bean = list.get(i);
/*  875 */         CARelation caRel = new CARelation();
/*  876 */         caRel.setRELATIONBRANCH((String)bean.get("Branch"));
/*  877 */         caRel.setRELATIONTYPE((String)bean.get("RelationType"));
/*  878 */         caRel.setRELATIONNUMBER((String)bean.get("RelationNumber"));
/*  879 */         bean.set("CARelation", caRel);
/*  880 */         bean.setDescription("CurrencyCode", CurrencyData.GetShortDescription(caller, (String)bean.get("CurrencyCode")));
/*  881 */         String regionCode = (String)bean.get("RegionCode");
/*  882 */         ChequesCABItem cabItem = (ChequesCABItem)cabList.get(regionCode);
/*  883 */         bean.setDescription("RegionCode", (cabItem != null) ? cabItem.getDescription() : regionCode);
/*  884 */         retInfo = getSlipRejectionReasons(caller, bean);
/*  885 */         ArrayList rejectReasons = (ArrayList)retInfo.getOutputParameter("RETURNLIST");
/*  886 */         bean.set("RejectionReasons", rejectReasons);
/*      */       } 
/*      */     } 
/*      */     
/*  890 */     ret.setOutputParameter("RETURNLIST", list);
/*      */     
/*  892 */     return ret.setOK();
/*      */   }
/*      */   
/*      */   public ReturnInfo getRejectionReasons(CallerInfo caller) throws ServiceException {
/*  896 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/*  897 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/*  898 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/*  900 */     StringBuffer sql = new StringBuffer("select a.C_MOT_RIFIU as VALUE, b.DZ as LABEL ");
/*  901 */     sql.append("from ICSR1E62 a, ICSR1I02 b ");
/*  902 */     sql.append("where a.C_IST=b.C_IST and a.ID_DZ=b.ID_DZ and  a.C_IST=$(BANK) and b.C_LNG='");
/*  903 */     sql.append(caller.getLanguage()).append("'");
/*      */ 
/*      */     
/*  906 */     sql.append(" AND a.DT_SCAD_VALD IS NULL ");
/*  907 */     sql.append(" AND b.DT_SCAD_VALD IS NULL ");
/*      */     
/*  909 */     sql.append(" order by C_MOT_RIFIU ");
/*      */ 
/*      */     
/*  912 */     LinkedHashMap<Object, Object> rejList = new LinkedHashMap<Object, Object>();
/*      */     try {
/*  914 */       CachedRowSet crs = SQLSelect(caller, sql.toString(), new Object[0]);
/*  915 */       if (crs != null) {
/*  916 */         while (crs.next()) {
/*  917 */           String value = crs.getString("VALUE");
/*  918 */           String label = crs.getString("LABEL");
/*      */ 
/*      */           
/*  921 */           rejList.put(value, label);
/*      */         } 
/*      */       }
/*  924 */     } catch (ServiceException e) {
/*  925 */       throw new ServiceException("Error getRejectionReasons(): ", e);
/*  926 */     } catch (SQLException e) {
/*  927 */       throw new ServiceException("Error getRejectionReasons(): ", e);
/*      */     } 
/*      */ 
/*      */     
/*  931 */     if (rejList == null || rejList.isEmpty())
/*  932 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1050", null)); 
/*  933 */     retInfo.setOutputParameter("RETURNLIST", rejList);
/*  934 */     retInfo.setOK();
/*  935 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo insertCollectionForeignCheques(CallerInfo caller, VODynaBean dynaBean) throws ServiceException {
/*  939 */     ReturnInfo ret = new ReturnInfo();
/*  940 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/*  941 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*  943 */     if (dynaBean == null || !dynaBean.getDynaClass().getName().equals("InsertCollForeignCheques")) {
/*  944 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "insertCollectionForeignCheques expecting VO InsertCollForeignCheques"));
/*      */     }
/*  946 */     String searchBricKey = "COLL.001";
/*  947 */     String returnBricKey = "COLL.501";
/*      */     
/*  949 */     BricContainer container = new BricContainer();
/*  950 */     CachedRowSet crs = null;
/*      */     
/*  952 */     crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/*  954 */       CARelation caRel = (CARelation)dynaBean.get("CARelation");
/*      */       
/*  956 */       crs.moveToInsertRow();
/*  957 */       crs.updateString("N_CHEQ", (String)dynaBean.get("ChequeNumber"));
/*  958 */       crs.updateString("CT_BNK", (String)dynaBean.get("DraweeBank"));
/*  959 */       crs.updateString("C_NAZ_BNFC", (String)dynaBean.get("CountryDraweeBank"));
/*  960 */       crs.updateString("C_DV", (String)dynaBean.get("Currency"));
/*  961 */       crs.updateObject("IMP", ((ElsagAmount)dynaBean.get("Amount")).get4EIS());
/*  962 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/*  963 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/*  964 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/*  965 */       crs.updateString("NOM_BNFC_1", (String)dynaBean.get("BFCName1"));
/*  966 */       crs.updateString("NOM_BNFC_2", (String)dynaBean.get("BFCName2"));
/*  967 */       crs.updateString("C_TP_DOC_BNFC", (String)dynaBean.get("DocType"));
/*  968 */       crs.updateString("N_DOC_BNFC", (String)dynaBean.get("DocNumber"));
/*  969 */       crs.updateString("COMUNE_DOC", (String)dynaBean.get("DocIssueCity"));
/*  970 */       crs.updateString("DATA_RIL_DOC", ((ElsagDate)dynaBean.get("DocIssueDate")).getValue());
/*  971 */       crs.insertRow();
/*  972 */     } catch (SQLException e) {
/*  973 */       throw new ServiceException("Error SQL  insertCollectionForeignCheques(): " + e.getMessage(), "9000");
/*      */     } 
/*      */ 
/*      */     
/*  977 */     if (!container.ExecTransaction(caller)) {
/*  978 */       return container.getReturnInfo();
/*      */     }
/*  980 */     crs = container.getOutputRowSet(returnBricKey);
/*  981 */     if (crs == null) {
/*  982 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "9000", null));
/*      */     }
/*  984 */     String refNumber = "";
/*      */     
/*      */     try {
/*  987 */       crs.moveToCurrentRow();
/*  988 */       crs.beforeFirst();
/*  989 */       if (crs.next()) {
/*  990 */         refNumber = crs.getString("N_REF");
/*      */       }
/*  992 */     } catch (SQLException e) {
/*  993 */       e.printStackTrace();
/*      */     } 
/*      */     
/*  996 */     ret = container.getReturnInfo();
/*  997 */     ret.setOutputParameter("RETURNBEAN", refNumber);
/*  998 */     return ret.setOK();
/*      */   }
/*      */   
/*      */   public ReturnInfo getForeignChequesStatusList(CallerInfo caller) throws ServiceException {
/* 1002 */     return ChequesFunctions.GetForeignChequesStatusList(caller);
/*      */   }
/*      */   
/*      */   public ReturnInfo searchCollectionForeignCheques(CallerInfo caller, VODynaBean dynaBean) throws ServiceException {
/* 1006 */     ReturnInfo ret = new ReturnInfo();
/* 1007 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1008 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1010 */     if (dynaBean == null || !dynaBean.getDynaClass().getName().equals("SearchForeignCheques")) {
/* 1011 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "searchCollectionForeignCheques expecting VO SearchForeignCheques"));
/*      */     }
/* 1013 */     String searchBricKey = "COLL.004";
/* 1014 */     String returnBricKey = "COLL.504";
/*      */     
/* 1016 */     BricContainer container = new BricContainer();
/* 1017 */     CachedRowSet crs = null;
/*      */     
/* 1019 */     crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/* 1021 */       crs.moveToInsertRow();
/* 1022 */       crs.updateString("C_AG_INS", (String)dynaBean.get("CollBranch"));
/*      */       
/* 1024 */       crs.updateObject("DT_FROM", ((ElsagDate)dynaBean.get("DtFrom")).get4EIS());
/* 1025 */       crs.updateObject("DT_TO", ((ElsagDate)dynaBean.get("DtTo")).get4EIS());
/* 1026 */       crs.updateString("C_STATUS", (String)dynaBean.get("CdStatus"));
/*      */       
/* 1028 */       crs.updateString("N_REF", (String)dynaBean.get("RefNumber"));
/* 1029 */       crs.insertRow();
/* 1030 */     } catch (SQLException e) {
/* 1031 */       throw new ServiceException("Error SQL searchCollectionForeignCheques(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 1034 */     if (!container.ExecuteQuery(caller)) {
/* 1035 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1038 */     if (!container.TransferToVOList(returnBricKey, "ResultForeignCheques")) {
/* 1039 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1042 */     ArrayList<VODynaBean> list = container.getReturnVOList("ResultForeignCheques");
/*      */     
/* 1044 */     for (int i = 0; i < list.size(); i++) {
/* 1045 */       VODynaBean vo = list.get(i);
/* 1046 */       if (!StringUtils.isEmpty((String)vo.get("BenefBranch")) && 
/* 1047 */         !StringUtils.isEmpty((String)vo.get("BenefRelType")) && 
/* 1048 */         !StringUtils.isEmpty((String)vo.get("BenefRelNumber"))) {
/* 1049 */         CARelation ca = new CARelation((String)vo.get("BenefBranch"), (String)vo.get("BenefRelType"), (String)vo.get("BenefRelNumber"));
/* 1050 */         vo.set("CARelation", ca);
/*      */       } 
/*      */     } 
/*      */     
/* 1054 */     ret.setOutputParameter("RETURNLIST", list);
/* 1055 */     return ret.setOK();
/*      */   }
/*      */   
/*      */   public ReturnInfo cancelForeignCheques(CallerInfo caller, ArrayList<VODynaBean> changeStatusList) throws ServiceException {
/* 1059 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 1061 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1062 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*      */     
/* 1065 */     AuthzKeyItem itemAuthKey = new AuthzKeyItem("SB20", "06", "15", "");
/* 1066 */     ReturnInfo retAuth = AuthUtil.VerifyAuthorizationNeed(caller, itemAuthKey);
/* 1067 */     if (!retAuth.getReturnCode().equals("0")) return retAuth; 
/* 1068 */     String sAuthID = (String)retAuth.getOutputParameter("OBJECTAUTHID");
/*      */     
/* 1070 */     String updateBricKey = "COLL.003";
/* 1071 */     BricContainer container = new BricContainer();
/* 1072 */     CachedRowSet crs = container.createRowSet(updateBricKey, "0002");
/*      */     
/*      */     try {
/* 1075 */       for (int i = 0; i < changeStatusList.size(); i++) {
/* 1076 */         VODynaBean ChangeStatusVO = changeStatusList.get(i);
/*      */         
/* 1078 */         if (ChangeStatusVO == null || !ChangeStatusVO.getDynaClass().getName().equals("ChangeStatusForeignCheques")) {
/* 1079 */           return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "cancelForeignCheques expecting VO ChangeStatusForeignCheques"));
/*      */         }
/* 1081 */         crs.moveToInsertRow();
/*      */         
/* 1083 */         crs.updateString("N_REF", (String)ChangeStatusVO.get("RefNumber"));
/* 1084 */         crs.updateString("C_STATUS", (String)ChangeStatusVO.get("Status"));
/*      */         
/* 1086 */         crs.updateBigDecimal("CMB_RIF", new BigDecimal(0));
/* 1087 */         crs.updateBigDecimal("CMB", new BigDecimal(0));
/*      */         
/* 1089 */         crs.updateString("C_TP_PAYMENT", (String)ChangeStatusVO.get("PayMethod"));
/*      */         
/* 1091 */         crs.insertRow();
/*      */       }
/*      */     
/* 1094 */     } catch (SQLException e) {
/* 1095 */       throw new ServiceException(" Error cancelForeignCheques() " + e.getMessage(), "9000", e);
/*      */     } 
/*      */     
/* 1098 */     retAuth = AuthUtil.PrepareAuthorizationBuffers(caller, sAuthID);
/* 1099 */     container.AddLinkedHashMap((LinkedHashMap)retAuth.removeOutputParameter("RETURNROWSETS"));
/*      */ 
/*      */     
/* 1102 */     if (!container.ExecTransaction(caller))
/* 1103 */       return container.getReturnInfo(); 
/* 1104 */     retInfo = container.getReturnInfo();
/* 1105 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo receiveFromBranchForeignCheques(CallerInfo caller, ArrayList changeStatusList) throws ServiceException {
/* 1109 */     return prepareFCChangeStatusWithAccounting(caller, changeStatusList, "01");
/*      */   }
/*      */   
/*      */   public ReturnInfo rejectForeignCheques(CallerInfo caller, ArrayList changeStatusList) throws ServiceException {
/* 1113 */     return prepareFCChangeStatusWithAccounting(caller, changeStatusList, "02");
/*      */   }
/*      */ 
/*      */   
/*      */   public ReturnInfo prepareFCChangeStatusWithAccounting(CallerInfo caller, ArrayList<VODynaBean> changeStatusList, String newStatus) throws ServiceException {
/* 1118 */     ReturnInfo ret = new ReturnInfo();
/* 1119 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1120 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1122 */     BricContainer container = new BricContainer();
/*      */     
/*      */     try {
/* 1125 */       for (int i = 0; i < changeStatusList.size(); i++) {
/* 1126 */         VODynaBean VO_1 = changeStatusList.get(i);
/* 1127 */         VODynaBean VO_2 = VOFactory.create("ResultForeignCheques");
/* 1128 */         VO_2.copyProperty(VO_1);
/* 1129 */         VO_2.set("Status", newStatus);
/*      */         
/* 1131 */         ForeignChequesObject objDebit = new ForeignChequesObject(Role.Debit);
/* 1132 */         objDebit.setCallerInfo(caller);
/* 1133 */         ret = objDebit.setData(VO_1, "000", null);
/* 1134 */         if (ret.isError()) return ret;
/*      */         
/* 1136 */         ForeignChequesObject objCredit = new ForeignChequesObject(Role.Credit);
/* 1137 */         objCredit.setCallerInfo(caller);
/* 1138 */         ret = objCredit.setData(VO_2, "000", null);
/* 1139 */         if (ret.isError()) return ret;
/*      */         
/* 1141 */         ret = objDebit.ExpandObject();
/* 1142 */         if (ret.isError()) return ret;
/*      */         
/* 1144 */         ret = objCredit.ExpandObject();
/* 1145 */         if (ret.isError()) return ret;
/*      */ 
/*      */         
/* 1148 */         String[] transList = AccountingData.getTransactionCode(objDebit.GetObjectKey(), objCredit.GetObjectKey());
/* 1149 */         String transCodeDebit = transList[0];
/* 1150 */         String transCodeCredit = transList[1];
/* 1151 */         if (transCodeDebit == null || transCodeCredit == null) {
/* 1152 */           log.error("******** ERROR ****** Transaction Code not found for cross debit:" + objDebit.GetObjectKey().toString() + " and Credit:" + objCredit.GetObjectKey().toString());
/* 1153 */           return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "2005", null));
/*      */         } 
/*      */ 
/*      */ 
/*      */         
/* 1158 */         container.AddLinkedHashMap(objCredit.PrepareData(transCodeCredit, objCredit.getCounterValue(), -1.0D, objCredit.getAmount(), objDebit.getAmount()));
/* 1159 */         container.AddLinkedHashMap(objDebit.PrepareAccounting(transCodeDebit, objDebit.getCounterValue(), -1.0D, objDebit.getAmount(), objCredit.getAmount()));
/* 1160 */         container.AddLinkedHashMap(objCredit.PrepareAccounting(transCodeCredit, objCredit.getCounterValue(), -1.0D, objCredit.getAmount(), objDebit.getAmount()));
/*      */       }
/*      */     
/* 1163 */     } catch (BusinessException e) {
/* 1164 */       throw new ServiceException("Error: paymentForeignCheques() ", e);
/*      */     } 
/* 1166 */     if (!container.ExecTransaction(caller))
/* 1167 */       return container.getReturnInfo(); 
/* 1168 */     ret = container.getReturnInfo();
/* 1169 */     return ret;
/*      */   }
/*      */ 
/*      */   
/*      */   public ReturnInfo paymentForeignCheques(CallerInfo caller, VODynaBean dynaBean, ExchangeRateItem exchangeItem) throws ServiceException {
/* 1174 */     ReturnInfo ret = new ReturnInfo();
/* 1175 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1176 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1178 */     if (dynaBean == null || !dynaBean.getDynaClass().getName().equals("ResultForeignCheques")) {
/* 1179 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/*      */ 
/*      */     
/* 1183 */     CARelation caRel = (CARelation)dynaBean.get("CARelation");
/* 1184 */     String payMethod = (String)dynaBean.get("PayMethod");
/* 1185 */     double dAmount = ((ElsagAmount)dynaBean.get("Amount")).getValue().doubleValue();
/* 1186 */     BigDecimal customerRate = (BigDecimal)dynaBean.get("CustomerRate");
/* 1187 */     ElsagAmount counterValue = new ElsagAmount(dAmount * customerRate.doubleValue());
/*      */     
/* 1189 */     ForeignChequesObject foreignChequesObject = new ForeignChequesObject(Role.Debit);
/*      */     try {
/* 1191 */       foreignChequesObject.setCallerInfo(caller);
/*      */ 
/*      */       
/* 1194 */       ret = foreignChequesObject.setData(dynaBean, "000", exchangeItem);
/* 1195 */       if (ret.isError()) {
/* 1196 */         return ret;
/*      */       }
/* 1198 */     } catch (BusinessException e) {
/* 1199 */       throw new ServiceException("Error: paymentForeignCheques() ", e);
/*      */     } 
/*      */ 
/*      */     
/* 1203 */     CurrentAccountObject caObject = null;
/* 1204 */     LocalPaymentObject localPayObject = null;
/* 1205 */     if ("1".equals(payMethod)) {
/*      */       
/* 1207 */       ret = CAFunctions.getCAWorkInfo(caller, caRel, false);
/* 1208 */       if (ret.isError())
/* 1209 */         return ret; 
/* 1210 */       VODynaBean caWorkInfoVO = (VODynaBean)ret.getOutputParameter("CAWorkInfoBean");
/* 1211 */       if (caWorkInfoVO == null) {
/* 1212 */         return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "1050", caRel.toString()));
/*      */       }
/* 1214 */       VODynaBean VOTransfer = VOFactory.create("CurrAccTransferVO");
/* 1215 */       VOTransfer.set("ValueDate", new ElsagDate(new Date()));
/* 1216 */       VOTransfer.set("Remark", "");
/* 1217 */       VOTransfer.set("CAWorkInfo", caWorkInfoVO);
/*      */       
/* 1219 */       caObject = new CurrentAccountObject(Role.Credit);
/*      */       try {
/* 1221 */         caObject.setCallerInfo(caller);
/* 1222 */         ret = caObject.setData(VOTransfer, "281", null);
/* 1223 */         if (ret.isError()) {
/* 1224 */           return ret;
/*      */         }
/* 1226 */       } catch (BusinessException e) {
/* 1227 */         throw new ServiceException("Error: paymentForeignCheques() ", e);
/*      */       }
/*      */     
/*      */     } else {
/*      */       
/* 1232 */       VODynaBean localPaymentVO = VOFactory.create("LocalPaymentCreditVO");
/*      */ 
/*      */       
/* 1235 */       VODynaBean orderVO = VOFactory.create("OrderValueObject");
/* 1236 */       localPaymentVO.set("OrderVO", orderVO);
/*      */ 
/*      */       
/* 1239 */       VODynaBean remitterInformation = VOFactory.create("InformationItem");
/* 1240 */       VODynaBean addressVO = VOFactory.create("AddressItem");
/* 1241 */       addressVO.set("Shortheading", " ");
/* 1242 */       addressVO.set("Address", " ");
/* 1243 */       addressVO.set("City", " ");
/* 1244 */       remitterInformation.set("AddressVO", addressVO);
/* 1245 */       VODynaBean remitterVO = VOFactory.create("RemitterValueObject");
/* 1246 */       remitterVO.set("RemitterInformation", remitterInformation);
/* 1247 */       localPaymentVO.set("RemitterVO", remitterVO);
/*      */ 
/*      */       
/* 1250 */       VODynaBean beneficiaryInformation = VOFactory.create("InformationItem");
/* 1251 */       VODynaBean bnfcAddressVO = VOFactory.create("AddressItem");
/* 1252 */       bnfcAddressVO.set("Shortheading", " ");
/* 1253 */       bnfcAddressVO.set("Address", " ");
/* 1254 */       bnfcAddressVO.set("City", " ");
/* 1255 */       beneficiaryInformation.set("AddressVO", bnfcAddressVO);
/* 1256 */       VODynaBean beneficiaryVO = VOFactory.create("BeneficiaryValueObject");
/* 1257 */       beneficiaryVO.set("BeneficiaryInformation", beneficiaryInformation);
/* 1258 */       localPaymentVO.set("BeneficiaryVO", beneficiaryVO);
/*      */ 
/*      */       
/* 1261 */       VODynaBean sponsorInformation = VOFactory.create("InformationItem");
/* 1262 */       VODynaBean sponsorAddressVO = VOFactory.create("AddressItem");
/* 1263 */       sponsorAddressVO.set("Shortheading", " ");
/* 1264 */       sponsorAddressVO.set("Address", " ");
/* 1265 */       sponsorAddressVO.set("City", " ");
/* 1266 */       sponsorInformation.set("AddressVO", sponsorAddressVO);
/* 1267 */       VODynaBean sponsorVO = VOFactory.create("SponsorValueObject");
/* 1268 */       sponsorVO.set("SponsorInformation", sponsorInformation);
/* 1269 */       localPaymentVO.set("SponsorVO", sponsorVO);
/*      */ 
/*      */ 
/*      */       
/* 1273 */       localPaymentVO.set("Branch", caller.getBranch());
/* 1274 */       localPaymentVO.set("NeedSponsor", "false");
/* 1275 */       orderVO.set("Currency", CurrencyData.GetLocalCurrency());
/* 1276 */       orderVO.set("Amount", counterValue);
/* 1277 */       orderVO.set("DispositionType", "05");
/* 1278 */       orderVO.set("ValidityDate", new ElsagDate(new Date()));
/*      */       
/* 1280 */       orderVO.set("Remark", "Cheque No. " + dynaBean.get("ChequeNumber"));
/*      */ 
/*      */       
/* 1283 */       remitterInformation.set("Name1", dynaBean.get("DraweeBank"));
/* 1284 */       remitterInformation.set("DocumentType", dynaBean.get("DocType"));
/* 1285 */       remitterInformation.set("DocumentNumber", dynaBean.get("DocNumber"));
/* 1286 */       remitterInformation.set("IssueCity", dynaBean.get("DocIssueCity"));
/* 1287 */       remitterInformation.set("IssueDate", dynaBean.get("DocIssueDate"));
/* 1288 */       remitterVO.set("CurrentAccount", caRel);
/*      */ 
/*      */       
/* 1291 */       beneficiaryInformation.set("Name1", dynaBean.get("BFCName1"));
/* 1292 */       beneficiaryInformation.set("Name2", dynaBean.get("BFCName2"));
/* 1293 */       beneficiaryInformation.set("DocumentType", dynaBean.get("DocType"));
/* 1294 */       beneficiaryInformation.set("DocumentNumber", dynaBean.get("DocNumber"));
/* 1295 */       beneficiaryInformation.set("IssueCity", dynaBean.get("DocIssueCity"));
/* 1296 */       beneficiaryInformation.set("IssueDate", dynaBean.get("DocIssueDate"));
/*      */ 
/*      */ 
/*      */       
/* 1300 */       localPayObject = new LocalPaymentObject(Role.Credit, "label.object.name.localPayment");
/*      */       try {
/* 1302 */         localPayObject.setCallerInfo(caller);
/* 1303 */         ret = localPayObject.setData(localPaymentVO, "165", null);
/* 1304 */         if (ret.isError()) {
/* 1305 */           return ret;
/*      */         }
/* 1307 */       } catch (BusinessException e) {
/* 1308 */         throw new ServiceException("Error: paymentForeignCheques() ", e);
/*      */       } 
/*      */     } 
/*      */ 
/*      */     
/*      */     try {
/* 1314 */       IDeskDelegateFactory iDeskClassFactory = (IDeskDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.desk.business.delegateFactory");
/* 1315 */       IDeskBusiness deskBusiness = iDeskClassFactory.createDesk();
/* 1316 */       Handle deskHandle = deskBusiness.GetHandle();
/*      */       
/* 1318 */       DeskController deskController = (DeskController)deskHandle.getEJBObject();
/* 1319 */       deskController.setCallerInfo(caller);
/*      */       
/* 1321 */       if (deskController == null) {
/* 1322 */         throw new ServiceException("Error: DeskController is Null");
/*      */       }
/* 1324 */       ReturnInfo retInfo = deskController.addBusinessObject((IAccountingBO)foreignChequesObject, -1);
/* 1325 */       if (retInfo.isError()) {
/* 1326 */         return retInfo;
/*      */       }
/* 1328 */       if (caObject != null) {
/* 1329 */         retInfo = deskController.addBusinessObject((IAccountingBO)caObject, -1);
/* 1330 */         if (retInfo.isError()) {
/* 1331 */           return retInfo;
/*      */         }
/*      */       } 
/* 1334 */       if (localPayObject != null) {
/* 1335 */         retInfo = deskController.addBusinessObject((IAccountingBO)localPayObject, -1);
/* 1336 */         if (retInfo.isError()) {
/* 1337 */           return retInfo;
/*      */         }
/*      */       } 
/*      */       
/* 1341 */       retInfo = deskController.RefreshDeskWithError();
/*      */       
/* 1343 */       if (retInfo.isError())
/* 1344 */         return retInfo; 
/* 1345 */       retInfo = deskController.ExecuteTransaction(caller, true, true);
/*      */       
/* 1347 */       return retInfo;
/*      */     }
/* 1349 */     catch (DeskException e) {
/* 1350 */       throw new ServiceException("Error: addBusinessObject() ", e);
/* 1351 */     } catch (RemoteException e) {
/* 1352 */       throw new ServiceException("Error: addBusinessObject() ", e);
/*      */     } 
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo verifyChequeAmount(CallerInfo caller, CARelation caRel, BigDecimal chequeNo) throws ServiceException {
/* 1359 */     ReturnInfo ret = new ReturnInfo();
/* 1360 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1361 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1363 */     String searchBricKey = "BCHQ.050";
/* 1364 */     String returnBricKey = "BCHQ.550";
/*      */     
/* 1366 */     BricContainer container = new BricContainer();
/* 1367 */     CachedRowSet crs = null;
/*      */     
/* 1369 */     crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/* 1371 */       crs.moveToInsertRow();
/* 1372 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 1373 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/* 1374 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/* 1375 */       crs.updateBigDecimal("N_CHEQ", chequeNo);
/* 1376 */       crs.insertRow();
/* 1377 */     } catch (SQLException e) {
/* 1378 */       throw new ServiceException("Error SQL verifyChequeAmount(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 1381 */     if (!container.ExecuteQuery(caller)) {
/* 1382 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1385 */     crs = container.getOutputRowSet(returnBricKey);
/*      */     try {
/* 1387 */       crs.moveToCurrentRow();
/* 1388 */       crs.beforeFirst();
/* 1389 */       crs.next();
/*      */       
/* 1391 */       String currency = crs.getString("C_DV");
/* 1392 */       ElsagAmount amount = new ElsagAmount();
/* 1393 */       amount.setByEIS(crs.getBigDecimal("IMP"));
/* 1394 */       amount.setCurrencySymbol(currency);
/* 1395 */       amount.changeValueByNewDecimal(CurrencyData.GetDecimalNumber(currency));
/*      */       
/* 1397 */       ret.setOutputParameter("AMOUNT", amount);
/* 1398 */       ret.setOK();
/* 1399 */     } catch (SQLException e) {
/* 1400 */       e.printStackTrace();
/*      */     } 
/*      */     
/* 1403 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo GetChequeLimitBySubcategory(CallerInfo caller, String strRelationType, String subCategory) throws ServiceException {
/* 1409 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 1410 */       return (new ReturnInfo()).addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 1411 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 1413 */     StringBuffer sql = new StringBuffer("SELECT IMP FROM ICSR1G17");
/* 1414 */     sql.append(" WHERE C_IST = $(BANK)");
/* 1415 */     sql.append(" AND C_TP_RAPP = '").append(strRelationType).append("'");
/* 1416 */     sql.append(" AND C_SCATG_RAPP = '").append(subCategory).append("'");
/* 1417 */     sql.append(" AND (C_AG = '").append(caller.getBranch()).append("' OR C_AG IS null) ");
/* 1418 */     sql.append(" AND DT_SCAD_VALD is null");
/*      */     
/* 1420 */     ElsagAmount imp = new ElsagAmount(0.0D);
/*      */     try {
/* 1422 */       CachedRowSet crs = SQLSelect(caller, sql.toString(), new Object[0]);
/* 1423 */       if (crs != null && 
/* 1424 */         crs.next()) {
/* 1425 */         imp = new ElsagAmount(crs.getBigDecimal("IMP"));
/*      */       }
/*      */     }
/* 1428 */     catch (ServiceException e) {
/* 1429 */       throw new ServiceException("Error GetChequeLimitBySubcategory(): ", e);
/* 1430 */     } catch (SQLException e) {
/* 1431 */       throw new ServiceException("Error GetChequeLimitBySubcategory(): ", e);
/*      */     } 
/*      */     
/* 1434 */     retInfo.setOutputParameter("RETURNVALUE", imp);
/* 1435 */     retInfo.setOK();
/* 1436 */     return retInfo;
/*      */   }
/*      */ 
/*      */   
/*      */   private ReturnInfo prepareInsertChequeImageID(CallerInfo caller, VODynaBean chequeImageID) throws ServiceException {
/* 1441 */     ReturnInfo ret = new ReturnInfo();
/*      */     
/* 1443 */     String chequeImageFrontId = (String)chequeImageID.get("ChequeImageIDFront");
/* 1444 */     String chequeImageBackId = (String)chequeImageID.get("ChequeImageIDBack");
/*      */     
/* 1446 */     if (StringUtils.isEmpty(chequeImageFrontId) && StringUtils.isEmpty(chequeImageBackId)) {
/* 1447 */       return ret.setOK();
/*      */     }
/*      */     
/* 1450 */     BricContainer container = new BricContainer();
/* 1451 */     CachedRowSet crs = container.createRowSet("BCHQ.016", "0002");
/*      */     try {
/* 1453 */       CARelation caRel = (CARelation)chequeImageID.get("CARelation");
/*      */       
/* 1455 */       crs.moveToInsertRow();
/* 1456 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 1457 */       crs.updateString("C_TP_RAPP", caRel.RELATIONTYPE);
/* 1458 */       crs.updateString("N_RAPP", caRel.RELATIONNUMBER);
/* 1459 */       crs.updateBigDecimal("N_ASS", (BigDecimal)chequeImageID.get("ChequeNumber"));
/* 1460 */       crs.updateString("ID_IMAG_FRONT", chequeImageFrontId);
/* 1461 */       crs.updateString("ID_IMAG_BACK", chequeImageBackId);
/* 1462 */       crs.insertRow();
/* 1463 */     } catch (SQLException e) {
/* 1464 */       throw new ServiceException("Error SQL prepareInsertChequeImageID(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 1467 */     ret.setOutputParameter("CONTAINER_OUT", container.getInput());
/* 1468 */     return ret.setOK();
/*      */   }
/*      */ 
/*      */   
/*      */   public ReturnInfo insertChequeImageID(CallerInfo caller, VODynaBean chequeImageID) throws ServiceException {
/* 1473 */     ReturnInfo ret = new ReturnInfo();
/* 1474 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 1475 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 1476 */     if (chequeImageID == null || !chequeImageID.getDynaClass().getName().equals("ChequeImageID")) {
/* 1477 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Insert cheque image ID"));
/*      */     }
/* 1479 */     ret = prepareInsertChequeImageID(caller, chequeImageID);
/* 1480 */     if (ret.isError()) {
/* 1481 */       return ret;
/*      */     }
/* 1483 */     BricContainer container = new BricContainer();
/*      */     
/* 1485 */     container.AddLinkedHashMap((LinkedHashMap)ret.removeOutputParameter("CONTAINER_OUT"));
/*      */     
/* 1487 */     if (!container.ExecTransaction(caller)) {
/* 1488 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1491 */     ret = container.getReturnInfo();
/* 1492 */     return ret;
/*      */   }
/*      */   
/*      */   public ReturnInfo insertRejectionSlipImageID(CallerInfo caller, VODynaBean slipImageID) throws ServiceException {
/* 1496 */     ReturnInfo ret = new ReturnInfo();
/* 1497 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 1498 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 1499 */     if (slipImageID == null || !slipImageID.getDynaClass().getName().equals("RejectionSlipImageID")) {
/* 1500 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Insert cheque image ID"));
/*      */     }
/* 1502 */     BricContainer container = new BricContainer();
/* 1503 */     CachedRowSet crs = null;
/* 1504 */     crs = container.createRowSet("BCHQ.017", "0002");
/*      */     try {
/* 1506 */       CARelation caRel = (CARelation)slipImageID.get("CARelation");
/*      */       
/* 1508 */       crs.moveToInsertRow();
/* 1509 */       crs.updateString("NDG", (String)slipImageID.get("CIC"));
/* 1510 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 1511 */       crs.updateString("C_TP_RAPP", caRel.RELATIONTYPE);
/* 1512 */       crs.updateString("N_RAPP", caRel.RELATIONNUMBER);
/* 1513 */       crs.updateBigDecimal("N_ASS", (BigDecimal)slipImageID.get("ChequeNumber"));
/* 1514 */       crs.updateBigDecimal("PRGR", (BigDecimal)slipImageID.get("SequenceNumber"));
/* 1515 */       crs.updateString("ID_IMAG_RIC", (String)slipImageID.get("SlipImageID"));
/* 1516 */       crs.insertRow();
/* 1517 */     } catch (SQLException e) {
/* 1518 */       throw new ServiceException("Error SQL  insertRejectionSlipImageID(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 1521 */     if (!container.ExecTransaction(caller)) {
/* 1522 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1525 */     ret = container.getReturnInfo();
/* 1526 */     return ret;
/*      */   }
/*      */   
/*      */   public ReturnInfo getSlipRejectionReasons(CallerInfo caller, VODynaBean slip) throws ServiceException {
/* 1530 */     ReturnInfo ret = new ReturnInfo();
/* 1531 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1532 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1534 */     if (slip == null || !slip.getDynaClass().getName().equals("SlipDetails")) {
/* 1535 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "getSlipRejectionReasons expecting VO SlipDetails"));
/*      */     }
/* 1537 */     String searchBricKey = "BCHQ.055";
/* 1538 */     String returnBricKey = "BCHQ.555";
/*      */     
/* 1540 */     BricContainer container = new BricContainer();
/* 1541 */     CachedRowSet crs = null;
/*      */     
/* 1543 */     crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/* 1545 */       CARelation caRel = (CARelation)slip.get("CARelation");
/*      */       
/* 1547 */       crs.moveToInsertRow();
/* 1548 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 1549 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/* 1550 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/* 1551 */       crs.updateBigDecimal("N_ASS", (BigDecimal)slip.get("ChequeNumber"));
/* 1552 */       crs.updateBigDecimal("PRGR", (BigDecimal)slip.get("SequenceNumber"));
/* 1553 */       crs.insertRow();
/* 1554 */     } catch (SQLException e) {
/* 1555 */       throw new ServiceException("Error SQL  getSlipRejectionReasons(): " + e.getMessage(), "9000");
/*      */     } 
/* 1557 */     if (!container.ExecuteQuery(caller)) {
/* 1558 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1561 */     ReturnInfo retInfo = getRejectionReasons(caller);
/* 1562 */     LinkedHashMap reasonList = (LinkedHashMap)retInfo.getOutputParameter("RETURNLIST");
/* 1563 */     CachedRowSet returnCRS = container.getOutputRowSet(returnBricKey);
/* 1564 */     ArrayList<LabelValueBean> list = new ArrayList();
/*      */     try {
/* 1566 */       if (returnCRS != null) {
/* 1567 */         while (returnCRS.next()) {
/* 1568 */           String reasonValue = returnCRS.getString("C_MOT_RIFIU");
/* 1569 */           list.add(new LabelValueBean((String)reasonList.get(reasonValue), reasonValue));
/*      */         }
/*      */       
/*      */       }
/* 1573 */     } catch (Exception e) {
/* 1574 */       log.error("Error in retrieving reject reasons " + e.getMessage(), e);
/* 1575 */       throw new ServiceException("Error in retrieving reject reasons : " + e.getMessage(), e);
/*      */     } 
/*      */     
/* 1578 */     ret.setOutputParameter("RETURNLIST", list);
/* 1579 */     return ret.setOK();
/*      */   }
/*      */   
/*      */   public ReturnInfo searchChequeImageID(CallerInfo caller, VODynaBean slip) throws ServiceException {
/* 1583 */     ReturnInfo ret = new ReturnInfo();
/* 1584 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1585 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1587 */     if (slip == null || !slip.getDynaClass().getName().equals("SlipDetails")) {
/* 1588 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "searchChequeImageID expecting VO SlipDetails"));
/*      */     }
/* 1590 */     String searchBricKey = "BCHQ.056";
/* 1591 */     String returnBricKey = "BCHQ.556";
/*      */     
/* 1593 */     BricContainer container = new BricContainer();
/* 1594 */     CachedRowSet crs = null;
/*      */     
/* 1596 */     crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/* 1598 */       CARelation caRel = (CARelation)slip.get("CARelation");
/*      */       
/* 1600 */       crs.moveToInsertRow();
/* 1601 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 1602 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/* 1603 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/* 1604 */       crs.updateBigDecimal("N_ASS", (BigDecimal)slip.get("ChequeNumber"));
/* 1605 */       crs.insertRow();
/* 1606 */     } catch (SQLException e) {
/* 1607 */       throw new ServiceException("Error SQL  searchChequeImageID(): " + e.getMessage(), "9000");
/*      */     } 
/* 1609 */     if (!container.ExecuteQuery(caller)) {
/* 1610 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1613 */     if (!container.TransferToVOList(returnBricKey, "RejectionSlipImageID")) {
/* 1614 */       return container.getReturnInfo();
/*      */     }
/* 1616 */     ArrayList list = container.getReturnVOList("SlipDetails");
/*      */     
/* 1618 */     ret.setOutputParameter("RETURNLIST", list);
/* 1619 */     return ret.setOK();
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getChequeImageSeqId(CallerInfo caller, String branchCode) throws ServiceException {
/* 1632 */     return ChequesFunctions.getChequeImageSeqId(caller, branchCode);
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getChequeRejectionControls(CallerInfo caller) throws ServiceException {
/* 1638 */     return ChequesFunctions.getChequeRejectionControls(caller);
/*      */   }
/*      */   
/*      */   public ReturnInfo searchChequeStatus(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 1642 */     ReturnInfo ret = new ReturnInfo();
/* 1643 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1644 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1646 */     if (searchVO == null || !searchVO.getDynaClass().getName().equals("ChequesStatusSearch")) {
/* 1647 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "searchChequeStatus expecting VO ChequesStatusSearch"));
/*      */     }
/* 1649 */     String searchBricKey = "BCHQ.057";
/* 1650 */     String returnBricKey = "BCHQ.557";
/*      */     
/* 1652 */     BricContainer container = new BricContainer();
/* 1653 */     CachedRowSet crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/* 1655 */       CARelation caRel = (CARelation)searchVO.get("CARelation");
/*      */       
/* 1657 */       crs.moveToInsertRow();
/* 1658 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 1659 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/* 1660 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/* 1661 */       crs.updateBigDecimal("N_ASS", (BigDecimal)searchVO.get("ChequeNumber"));
/* 1662 */       crs.insertRow();
/* 1663 */     } catch (SQLException e) {
/* 1664 */       throw new ServiceException("Error SQL  searchChequeStatus(): " + e.getMessage(), "9000");
/*      */     } 
/* 1666 */     if (!container.ExecuteQuery(caller)) {
/* 1667 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1670 */     String status = null;
/* 1671 */     crs = container.getOutputRowSet(returnBricKey);
/* 1672 */     if (crs != null) {
/*      */       try {
/* 1674 */         crs.beforeFirst();
/* 1675 */         if (crs.next()) {
/* 1676 */           status = crs.getString("C_ST");
/*      */         }
/* 1678 */       } catch (SQLException e) {
/* 1679 */         throw new ServiceException("Error searchChequeStatus", e);
/*      */       } 
/*      */     }
/*      */     
/* 1683 */     ret = container.getReturnInfo();
/* 1684 */     ret.setOutputParameter("STATUS", status);
/* 1685 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getChequeAdditionalControlParameters(CallerInfo caller, String conventionCode, String relationType, String subcategory) throws ServiceException {
/* 1700 */     return ChequesFunctions.getChequeAdditionalControlParameters(caller, conventionCode, relationType, subcategory);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getLastChequeNumber(CallerInfo caller, CARelation caRel) throws ServiceException {
/* 1710 */     ReturnInfo ret = new ReturnInfo();
/* 1711 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1712 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 1714 */     if (caRel == null || StringUtils.isEmpty(caRel.toString())) {
/* 1715 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "getLastChequeNumber() - CARelation"));
/*      */     }
/* 1717 */     String searchBricKey = "BCHQ.058";
/* 1718 */     String returnBricKey = "BCHQ.558";
/*      */     
/* 1720 */     BricContainer container = new BricContainer();
/* 1721 */     CachedRowSet crs = container.createRowSet(searchBricKey, "0002");
/*      */     try {
/* 1723 */       crs.moveToInsertRow();
/* 1724 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 1725 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/* 1726 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/* 1727 */       crs.insertRow();
/* 1728 */     } catch (SQLException e) {
/* 1729 */       throw new ServiceException("Error SQL getLastChequeNumber(): " + e.getMessage(), "9000");
/*      */     } 
/* 1731 */     if (!container.ExecuteQuery(caller)) {
/* 1732 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 1735 */     BigDecimal lastChequeNumber = null;
/* 1736 */     CARelation lastCARel = null;
/* 1737 */     crs = container.getOutputRowSet(returnBricKey);
/* 1738 */     if (crs != null) {
/*      */       try {
/* 1740 */         crs.beforeFirst();
/* 1741 */         if (crs.next()) {
/* 1742 */           lastChequeNumber = crs.getBigDecimal("N_ASS");
/* 1743 */           lastCARel = new CARelation();
/* 1744 */           lastCARel.setRELATIONBRANCH(crs.getString("C_AG"));
/* 1745 */           lastCARel.setRELATIONTYPE(crs.getString("C_TP_RAPP"));
/* 1746 */           lastCARel.setRELATIONNUMBER(crs.getString("N_RAPP"));
/*      */         } 
/* 1748 */       } catch (SQLException e) {
/* 1749 */         throw new ServiceException("Error getLastChequeNumber()", e);
/*      */       } 
/*      */     }
/*      */     
/* 1753 */     ret = container.getReturnInfo();
/* 1754 */     if (lastChequeNumber != null)
/* 1755 */       ret.setOutputParameter("LAST_CHEQUE_NO", lastChequeNumber); 
/* 1756 */     if (lastCARel != null)
/* 1757 */       ret.setOutputParameter("LAST_CA", lastCARel); 
/* 1758 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo searchChequesByDate(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 1764 */     ReturnInfo ret = new ReturnInfo();
/* 1765 */     ret.setOK();
/*      */ 
/*      */     
/* 1768 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 1769 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/*      */     
/* 1772 */     String searchVOName = "SearchChequesByDateVO";
/* 1773 */     if (searchVO == null || !searchVO.getDynaClass().getName().equals(searchVOName)) {
/* 1774 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/* 1776 */     CARelation caRelation = (CARelation)searchVO.get("CARelation");
/* 1777 */     ElsagDate valueDate = (ElsagDate)searchVO.get("ValueDate");
/*      */     
/* 1779 */     if (StringUtils.isEmpty(caRelation.getRELATIONBRANCH()))
/* 1780 */       ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "C/A Branch field is mandatory")); 
/* 1781 */     if (StringUtils.isEmpty(caRelation.getRELATIONTYPE()))
/* 1782 */       ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "C/A Type field is mandatory")); 
/* 1783 */     if (StringUtils.isEmpty(caRelation.getRELATIONNUMBER()))
/* 1784 */       ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "C/A Number field is mandatory")); 
/* 1785 */     if (StringUtils.isEmpty(caRelation.toString())) {
/* 1786 */       ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "C/A Number is invalid"));
/*      */     }
/* 1788 */     if (ret.isError()) {
/* 1789 */       return ret;
/*      */     }
/* 1791 */     String searchBricKey = "CRCHQ.003";
/* 1792 */     String returnBricKey = "CRCHQ.503";
/* 1793 */     String returnVOName = "ChequeInfoByDateVO";
/*      */     
/* 1795 */     BricContainer container = new BricContainer();
/* 1796 */     CachedRowSet crs = container.createRowSet(searchBricKey, "0002");
/*      */     
/*      */     try {
/* 1799 */       crs.moveToInsertRow();
/* 1800 */       crs.updateString("C_AG", caRelation.getRELATIONBRANCH());
/* 1801 */       crs.updateString("C_TP_RAPP", caRelation.getRELATIONTYPE());
/* 1802 */       crs.updateString("N_RAPP", caRelation.getRELATIONNUMBER());
/* 1803 */       crs.updateObject("DT_VAL", valueDate.get4EIS());
/* 1804 */       crs.insertRow();
/*      */     }
/* 1806 */     catch (SQLException e) {
/* 1807 */       throw new ServiceException("Error SQL  searchChequesByDate():" + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 1810 */     if (!container.ExecuteQuery(caller)) {
/* 1811 */       return container.getReturnInfo();
/*      */     }
/* 1813 */     if (!container.TransferToVOList(returnBricKey, returnVOName, false)) {
/* 1814 */       return container.getReturnInfo();
/*      */     }
/* 1816 */     ArrayList listVO = container.getReturnVOList(returnVOName);
/*      */     
/* 1818 */     ret = container.getReturnInfo();
/* 1819 */     ret.setOutputParameter("RETURNLIST", listVO);
/* 1820 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo searchClearingCheques(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 1826 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 1828 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 1829 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 1830 */     if (searchVO == null || !searchVO.getDynaClass().getName().equals("SearchClearingCheques")) {
/* 1831 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Expecting VO of type SearchClearingCheques"));
/*      */     }
/* 1833 */     String inputBuffer = "BCHQ.060";
/* 1834 */     String outputBuffer = "BCHQ.560";
/*      */     
/* 1836 */     ReturnInfo retInfoCount = getClearingChequesCount(caller, searchVO);
/* 1837 */     if (retInfoCount.isError()) {
/* 1838 */       return retInfoCount;
/*      */     }
/*      */     
/* 1841 */     Integer totalRowsCount = (Integer)retInfoCount.getOutputParameter("COUNT");
/*      */     
/* 1843 */     BricContainer bricCont = new BricContainer();
/*      */     
/* 1845 */     CachedRowSet crs = bricCont.createRowSet(inputBuffer, null);
/*      */     
/*      */     try {
/* 1848 */       crs.moveToInsertRow();
/* 1849 */       crs.updateString("C_STATUS", (String)searchVO.get("ChequeStatus"));
/* 1850 */       crs.updateString("C_ABI", (String)searchVO.get("Bank"));
/* 1851 */       crs.updateString("C_CAB", (String)searchVO.get("Region"));
/* 1852 */       crs.updateString("TP_ASS", (String)searchVO.get("ChequeType"));
/* 1853 */       crs.updateBigDecimal("N_ASS", (BigDecimal)searchVO.get("ChequeNumber"));
/* 1854 */       crs.updateString("N_CT_TRAE", (String)searchVO.get("DrawerRelationNo"));
/* 1855 */       crs.updateObject("DT_FROM", ((ElsagDate)searchVO.get("StartDate")).get4EIS());
/* 1856 */       crs.updateObject("DT_TO", ((ElsagDate)searchVO.get("EndDate")).get4EIS());
/* 1857 */       crs.updateString("C_AG", (String)searchVO.get("AccountingBranch"));
/*      */       
/* 1859 */       bricCont.DoPagination(inputBuffer, crs, searchVO, totalRowsCount);
/*      */       
/* 1861 */       crs.insertRow();
/* 1862 */     } catch (SQLException e) {
/* 1863 */       throw new ServiceException(" Error searchClearingCheques() " + e.getMessage(), "9000", e);
/*      */     } 
/*      */     
/* 1866 */     if (!bricCont.ExecuteQuery(caller)) {
/* 1867 */       return bricCont.getReturnInfo();
/*      */     }
/*      */     
/* 1870 */     if (!bricCont.TransferToVOList(outputBuffer, "ClearingChequesVO")) {
/* 1871 */       return bricCont.getReturnInfo();
/*      */     }
/* 1873 */     retInfo = bricCont.getReturnInfo();
/*      */     
/* 1875 */     ArrayList<VODynaBean> retVOList = bricCont.getReturnVOList("ClearingChequesVO");
/*      */     
/* 1877 */     if (retVOList != null) {
/*      */       
/* 1879 */       LinkedHashMap regionMap = RegionForLocalBankData.getRegionForLocalBankList(caller);
/*      */       
/* 1881 */       for (int i = 0; i < retVOList.size(); i++) {
/* 1882 */         VODynaBean chequeVO = retVOList.get(i);
/*      */         
/* 1884 */         String relationBranch = (String)chequeVO.get("RelationBranch");
/* 1885 */         String relationType = (String)chequeVO.get("RelationType");
/* 1886 */         String relationNumber = (String)chequeVO.get("RelationNumber");
/* 1887 */         CARelation caRel = new CARelation(relationBranch, relationType, relationNumber);
/* 1888 */         chequeVO.set("CARelation", caRel);
/*      */         
/* 1890 */         String currency = (String)chequeVO.get("Currency");
/* 1891 */         CurrencyItem currencyItem = CurrencyData.GetCurrencyItem(caller, currency);
/* 1892 */         chequeVO.setDescription("Currency", (currencyItem != null) ? currencyItem.getDescription() : currency);
/*      */         
/* 1894 */         ElsagAmount amount = (ElsagAmount)chequeVO.get("Amount");
/* 1895 */         if (currencyItem != null && currencyItem.getDecimals() != CurrencyData.GetLocalDecimals()) {
/* 1896 */           amount.changeValueByNewDecimal(currencyItem.getDecimals());
/*      */         }
/*      */         
/* 1899 */         String bank = (String)chequeVO.get("Bank");
/* 1900 */         String region = (String)chequeVO.get("Region");
/* 1901 */         String chequeType = (String)chequeVO.get("ChequeType");
/* 1902 */         BigDecimal chequeNumber = (BigDecimal)chequeVO.get("ChequeNumber");
/* 1903 */         String drawerRelationNo = (String)chequeVO.get("DrawerRelationNo");
/*      */         
/* 1905 */         StringBuffer trackingNumber = new StringBuffer("");
/* 1906 */         trackingNumber.append(StringUtils.fillChars(bank, ' ', 5, false));
/* 1907 */         trackingNumber.append(StringUtils.fillChars(region, ' ', 5, false));
/* 1908 */         trackingNumber.append(StringUtils.fillChars(chequeType, ' ', 1, false));
/* 1909 */         trackingNumber.append(StringUtils.fillChars(chequeNumber.toString(), ' ', 10, true));
/* 1910 */         trackingNumber.append(StringUtils.fillChars(drawerRelationNo, ' ', 8, false));
/*      */         
/* 1912 */         chequeVO.set("TrackingNumber", trackingNumber.toString());
/*      */         
/* 1914 */         CorrespBanksItem cbiItem = CorrespondentBankData.getCorrespBankItem(caller, bank);
/* 1915 */         chequeVO.setDescription("Bank", (cbiItem != null) ? cbiItem.getLabel() : bank);
/*      */         
/* 1917 */         String regionDesc = region;
/* 1918 */         if (cbiItem != null && "1".equals(cbiItem.getNaz())) {
/* 1919 */           LabelValueBean lvb = (LabelValueBean)regionMap.get(region);
/* 1920 */           if (lvb != null)
/* 1921 */             regionDesc = lvb.getLabel(); 
/*      */         } 
/* 1923 */         chequeVO.setDescription("Region", regionDesc);
/*      */       } 
/*      */     } 
/*      */     
/* 1927 */     retInfo.setOutputParameter("RETURNVOLIST", retVOList);
/*      */     
/* 1929 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo getClearingChequesCount(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 1933 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 1935 */     String searchKey = "BCHQ.059";
/*      */ 
/*      */     
/* 1938 */     String returnKey = "COUNT.550";
/*      */ 
/*      */     
/* 1941 */     BricContainer bricContainer = new BricContainer();
/* 1942 */     CachedRowSet crs = bricContainer.createRowSet(searchKey, null);
/*      */     
/*      */     try {
/* 1945 */       crs.moveToInsertRow();
/* 1946 */       crs.updateString("C_STATUS", (String)searchVO.get("ChequeStatus"));
/* 1947 */       crs.updateString("C_ABI", (String)searchVO.get("Bank"));
/* 1948 */       crs.updateString("C_CAB", (String)searchVO.get("Region"));
/* 1949 */       crs.updateString("TP_ASS", (String)searchVO.get("ChequeType"));
/* 1950 */       crs.updateBigDecimal("N_ASS", (BigDecimal)searchVO.get("ChequeNumber"));
/* 1951 */       crs.updateString("N_CT_TRAE", (String)searchVO.get("DrawerRelationNo"));
/* 1952 */       crs.updateObject("DT_FROM", ((ElsagDate)searchVO.get("StartDate")).get4EIS());
/* 1953 */       crs.updateObject("DT_TO", ((ElsagDate)searchVO.get("EndDate")).get4EIS());
/* 1954 */       crs.updateString("C_AG", (String)searchVO.get("AccountingBranch"));
/* 1955 */       crs.insertRow();
/* 1956 */     } catch (SQLException e) {
/* 1957 */       throw new ServiceException(" Error SQL getClearingChequesCount(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 1960 */     if (!bricContainer.ExecuteQuery(caller)) {
/* 1961 */       return bricContainer.getReturnInfo();
/*      */     }
/*      */     
/* 1964 */     CachedRowSet retCachedRowSet = bricContainer.getOutputRowSet(returnKey);
/*      */     
/* 1966 */     if (retCachedRowSet != null) {
/*      */       try {
/* 1968 */         retCachedRowSet.moveToCurrentRow();
/* 1969 */         retCachedRowSet.beforeFirst();
/* 1970 */         retCachedRowSet.next();
/*      */         
/* 1972 */         Integer count = Integer.valueOf(retCachedRowSet.getInt("COUNT"));
/* 1973 */         retInfo.setOutputParameter("COUNT", count);
/* 1974 */       } catch (SQLException e) {
/* 1975 */         throw new ServiceException(" Error getClearingChequesCount() ", e);
/*      */       } 
/*      */     }
/* 1978 */     return retInfo.setOK();
/*      */   }
/*      */   
/*      */   public ReturnInfo searchClearingChequesHistory(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 1982 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 1984 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 1985 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 1986 */     if (searchVO == null || !searchVO.getDynaClass().getName().equals("SearchClearingCheques")) {
/* 1987 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Expecting VO of type SearchClearingCheques"));
/*      */     }
/* 1989 */     String inputBuffer = "BCHQ.062";
/* 1990 */     String outputBuffer = "BCHQ.562";
/*      */     
/* 1992 */     ReturnInfo retInfoCount = getClearingChequesHistoryCount(caller, searchVO);
/* 1993 */     if (retInfoCount.isError()) {
/* 1994 */       return retInfoCount;
/*      */     }
/*      */     
/* 1997 */     Integer totalRowsCount = (Integer)retInfoCount.getOutputParameter("COUNT");
/*      */     
/* 1999 */     BricContainer bricCont = new BricContainer();
/*      */ 
/*      */     
/* 2002 */     CARelation relation = (CARelation)searchVO.get("CARelation");
/*      */ 
/*      */     
/* 2005 */     CachedRowSet crs = bricCont.createRowSet(inputBuffer, null);
/*      */     
/*      */     try {
/* 2008 */       crs.moveToInsertRow();
/* 2009 */       crs.updateString("C_ABI", (String)searchVO.get("Bank"));
/* 2010 */       crs.updateString("C_CAB", (String)searchVO.get("Region"));
/* 2011 */       crs.updateString("TP_ASS", (String)searchVO.get("ChequeType"));
/* 2012 */       crs.updateBigDecimal("N_ASS", (BigDecimal)searchVO.get("ChequeNumber"));
/* 2013 */       crs.updateString("N_CT_TRAE", (String)searchVO.get("DrawerRelationNo"));
/* 2014 */       crs.updateString("C_AG", (String)searchVO.get("AccountingBranch"));
/*      */       
/* 2016 */       crs.updateString("C_STATUS", (String)searchVO.get("ChequeStatus"));
/*      */ 
/*      */       
/* 2019 */       if (relation != null) {
/* 2020 */         crs.updateString("C_AG_RAPP", relation.getRELATIONBRANCH());
/* 2021 */         crs.updateString("C_TP_RAPP", relation.getRELATIONTYPE());
/* 2022 */         crs.updateString("N_RAPP", relation.getRELATIONNUMBER());
/* 2023 */         crs.updateObject("DT_FROM", ((ElsagDate)searchVO.get("TransactionDateFrom")).get4EIS());
/* 2024 */         crs.updateObject("DT_TO", ((ElsagDate)searchVO.get("TransactionDateTo")).get4EIS());
/*      */       } else {
/*      */         
/* 2027 */         crs.updateObject("DT_FROM", ((ElsagDate)searchVO.get("StartDate")).get4EIS());
/* 2028 */         crs.updateObject("DT_TO", ((ElsagDate)searchVO.get("EndDate")).get4EIS());
/*      */       } 
/* 2030 */       crs.updateObject("IMP_TO", searchVO.get4EIS("ChequeAmountTo"));
/* 2031 */       crs.updateObject("IMP_FROM", searchVO.get4EIS("ChequeAmountFrom"));
/* 2032 */       crs.updateObject("DT_ELAB_FROM", searchVO.get4EIS("RealizationDateFrom"));
/* 2033 */       crs.updateObject("DT_ELAB_TO", searchVO.get4EIS("RealizationDateTo"));
/* 2034 */       crs.updateObject("DT_VAL_FROM", searchVO.get4EIS("ValueDateFrom"));
/* 2035 */       crs.updateObject("DT_VAL_TO", searchVO.get4EIS("ValueDateTo"));
/*      */ 
/*      */       
/* 2038 */       bricCont.DoPagination(inputBuffer, crs, searchVO, totalRowsCount);
/*      */       
/* 2040 */       crs.insertRow();
/* 2041 */     } catch (SQLException e) {
/* 2042 */       throw new ServiceException(" Error searchClearingChequesHistory() " + e.getMessage(), "9000", e);
/*      */     } 
/*      */     
/* 2045 */     if (!bricCont.ExecuteQuery(caller)) {
/* 2046 */       return bricCont.getReturnInfo();
/*      */     }
/*      */     
/* 2049 */     if (!bricCont.TransferToVOList(outputBuffer, "ClearingChequesVO")) {
/* 2050 */       return bricCont.getReturnInfo();
/*      */     }
/* 2052 */     retInfo = bricCont.getReturnInfo();
/*      */     
/* 2054 */     ArrayList<VODynaBean> retVOList = bricCont.getReturnVOList("ClearingChequesVO");
/*      */     
/* 2056 */     if (retVOList != null) {
/*      */       
/* 2058 */       LinkedHashMap regionMap = RegionForLocalBankData.getRegionForLocalBankList(caller);
/*      */       
/* 2060 */       for (int i = 0; i < retVOList.size(); i++) {
/* 2061 */         VODynaBean chequeVO = retVOList.get(i);
/*      */         
/* 2063 */         String relationBranch = (String)chequeVO.get("RelationBranch");
/* 2064 */         String relationType = (String)chequeVO.get("RelationType");
/* 2065 */         String relationNumber = (String)chequeVO.get("RelationNumber");
/* 2066 */         CARelation caRel = new CARelation(relationBranch, relationType, relationNumber);
/* 2067 */         chequeVO.set("CARelation", caRel);
/*      */         
/* 2069 */         String currency = (String)chequeVO.get("Currency");
/* 2070 */         CurrencyItem currencyItem = CurrencyData.GetCurrencyItem(caller, currency);
/* 2071 */         chequeVO.setDescription("Currency", (currencyItem != null) ? currencyItem.getDescription() : currency);
/*      */         
/* 2073 */         ElsagAmount amount = (ElsagAmount)chequeVO.get("Amount");
/* 2074 */         if (currencyItem != null && currencyItem.getDecimals() != CurrencyData.GetLocalDecimals()) {
/* 2075 */           amount.changeValueByNewDecimal(currencyItem.getDecimals());
/*      */         }
/*      */         
/* 2078 */         String bank = (String)chequeVO.get("Bank");
/* 2079 */         String region = (String)chequeVO.get("Region");
/* 2080 */         String chequeType = (String)chequeVO.get("ChequeType");
/* 2081 */         BigDecimal chequeNumber = (BigDecimal)chequeVO.get("ChequeNumber");
/* 2082 */         String drawerRelationNo = (String)chequeVO.get("DrawerRelationNo");
/*      */         
/* 2084 */         StringBuffer trackingNumber = new StringBuffer("");
/* 2085 */         trackingNumber.append(StringUtils.fillChars(bank, ' ', 5, false));
/* 2086 */         trackingNumber.append(StringUtils.fillChars(region, ' ', 5, false));
/* 2087 */         trackingNumber.append(StringUtils.fillChars(chequeType, ' ', 1, false));
/* 2088 */         trackingNumber.append(StringUtils.fillChars(chequeNumber.toString(), ' ', 10, true));
/* 2089 */         trackingNumber.append(StringUtils.fillChars(drawerRelationNo, ' ', 8, false));
/*      */         
/* 2091 */         chequeVO.set("TrackingNumber", trackingNumber.toString());
/*      */         
/* 2093 */         CorrespBanksItem cbiItem = CorrespondentBankData.getCorrespBankItem(caller, bank);
/* 2094 */         chequeVO.setDescription("Bank", (cbiItem != null) ? cbiItem.getLabel() : bank);
/*      */         
/* 2096 */         String regionDesc = region;
/* 2097 */         if (cbiItem != null && "1".equals(cbiItem.getNaz())) {
/* 2098 */           LabelValueBean lvb = (LabelValueBean)regionMap.get(region);
/* 2099 */           if (lvb != null)
/* 2100 */             regionDesc = lvb.getLabel(); 
/*      */         } 
/* 2102 */         chequeVO.setDescription("Region", regionDesc);
/*      */       } 
/*      */     } 
/*      */     
/* 2106 */     retInfo.setOutputParameter("RETURNVOLIST", retVOList);
/*      */     
/* 2108 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo getClearingChequesHistoryCount(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 2112 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2114 */     String searchKey = "BCHQ.061";
/* 2115 */     String returnKey = "COUNT.550";
/*      */     
/* 2117 */     BricContainer bricContainer = new BricContainer();
/* 2118 */     CachedRowSet crs = bricContainer.createRowSet(searchKey, null);
/*      */     
/* 2120 */     CARelation relation = (CARelation)searchVO.get("CARelation");
/*      */ 
/*      */     
/*      */     try {
/* 2124 */       crs.moveToInsertRow();
/* 2125 */       crs.updateString("C_ABI", (String)searchVO.get("Bank"));
/* 2126 */       crs.updateString("C_CAB", (String)searchVO.get("Region"));
/* 2127 */       crs.updateString("TP_ASS", (String)searchVO.get("ChequeType"));
/* 2128 */       crs.updateBigDecimal("N_ASS", (BigDecimal)searchVO.get("ChequeNumber"));
/* 2129 */       crs.updateString("N_CT_TRAE", (String)searchVO.get("DrawerRelationNo"));
/* 2130 */       crs.updateString("C_AG", (String)searchVO.get("AccountingBranch"));
/*      */       
/* 2132 */       crs.updateString("C_STATUS", (String)searchVO.get("ChequeStatus"));
/*      */ 
/*      */       
/* 2135 */       if (relation != null) {
/* 2136 */         crs.updateString("C_AG_RAPP", relation.getRELATIONBRANCH());
/* 2137 */         crs.updateString("C_TP_RAPP", relation.getRELATIONTYPE());
/* 2138 */         crs.updateString("N_RAPP", relation.getRELATIONNUMBER());
/* 2139 */         crs.updateObject("DT_FROM", ((ElsagDate)searchVO.get("TransactionDateFrom")).get4EIS());
/* 2140 */         crs.updateObject("DT_TO", ((ElsagDate)searchVO.get("TransactionDateTo")).get4EIS());
/*      */       } else {
/*      */         
/* 2143 */         crs.updateObject("DT_FROM", ((ElsagDate)searchVO.get("StartDate")).get4EIS());
/* 2144 */         crs.updateObject("DT_TO", ((ElsagDate)searchVO.get("EndDate")).get4EIS());
/*      */       } 
/* 2146 */       crs.updateObject("IMP_TO", searchVO.get4EIS("ChequeAmountTo"));
/* 2147 */       crs.updateObject("IMP_FROM", searchVO.get4EIS("ChequeAmountFrom"));
/* 2148 */       crs.updateObject("DT_ELAB_FROM", searchVO.get4EIS("RealizationDateFrom"));
/* 2149 */       crs.updateObject("DT_ELAB_TO", searchVO.get4EIS("RealizationDateTo"));
/* 2150 */       crs.updateObject("DT_VAL_FROM", searchVO.get4EIS("ValueDateFrom"));
/* 2151 */       crs.updateObject("DT_VAL_TO", searchVO.get4EIS("ValueDateTo"));
/*      */ 
/*      */       
/* 2154 */       crs.insertRow();
/* 2155 */     } catch (SQLException e) {
/* 2156 */       throw new ServiceException(" Error SQL getClearingChequesHistoryCount(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 2159 */     if (!bricContainer.ExecuteQuery(caller)) {
/* 2160 */       return bricContainer.getReturnInfo();
/*      */     }
/*      */     
/* 2163 */     CachedRowSet retCachedRowSet = bricContainer.getOutputRowSet(returnKey);
/*      */     
/* 2165 */     if (retCachedRowSet != null) {
/*      */       try {
/* 2167 */         retCachedRowSet.moveToCurrentRow();
/* 2168 */         retCachedRowSet.beforeFirst();
/* 2169 */         retCachedRowSet.next();
/*      */         
/* 2171 */         Integer count = Integer.valueOf(retCachedRowSet.getInt("COUNT"));
/* 2172 */         retInfo.setOutputParameter("COUNT", count);
/* 2173 */       } catch (SQLException e) {
/* 2174 */         throw new ServiceException(" Error getClearingChequesHistoryCount() ", e);
/*      */       } 
/*      */     }
/* 2177 */     return retInfo.setOK();
/*      */   }
/*      */   
/*      */   public ReturnInfo changeStatusClearingCheque(CallerInfo caller, ArrayList chequeList) throws ServiceException {
/* 2181 */     ReturnInfo ret = new ReturnInfo();
/* 2182 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 2183 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 2185 */     BricContainer bricContainer = new BricContainer();
/*      */     
/* 2187 */     ret = ChequesFunctions.prepareChangeStatusClearingCheque(caller, chequeList);
/* 2188 */     if (ret.isError())
/* 2189 */       return ret; 
/* 2190 */     bricContainer.AddLinkedHashMap((LinkedHashMap)ret.removeOutputParameter("CONTAINER_OUT"));
/*      */     
/* 2192 */     if (!bricContainer.ExecTransaction(caller)) {
/* 2193 */       return bricContainer.getReturnInfo();
/*      */     }
/*      */     
/* 2196 */     ret = bricContainer.getReturnInfo();
/* 2197 */     return ret;
/*      */   }
/*      */   
/*      */   public LinkedHashMap<String, TransactionCodeItem> getTransactionCodeListForChequeRejection(CallerInfo caller) throws ServiceException {
/* 2201 */     return ChequesFunctions.getTransactionCodeListForChequeRejection(caller);
/*      */   }
/*      */   
/*      */   public ReturnInfo clearingChequeRejection(CallerInfo caller, VODynaBean VO) throws ServiceException {
/* 2205 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2207 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 2208 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 2210 */     if (!VO.getDynaClass().getName().equals("MovementCancellationObjectVO")) {
/* 2211 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */     
/* 2223 */     CurrentAccountObject caObject = new CurrentAccountObject(Role.Credit);
/* 2224 */     ClearingChequeRejectionObject clearingChequeRejectionObject = new ClearingChequeRejectionObject(Role.Debit);
/*      */ 
/*      */ 
/*      */     
/*      */     try {
/* 2229 */       IDeskDelegateFactory iDeskClassFactory = (IDeskDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.desk.business.delegateFactory");
/* 2230 */       IDeskBusiness deskBusiness = iDeskClassFactory.createDesk();
/* 2231 */       Handle deskHandle = deskBusiness.GetHandle();
/*      */       
/* 2233 */       DeskController deskController = (DeskController)deskHandle.getEJBObject();
/*      */       
/* 2235 */       if (deskController == null)
/* 2236 */         throw new ServiceException("Error: DeskController is Null"); 
/* 2237 */       deskController.setCallerInfo(caller);
/*      */       
/* 2239 */       CARelation caRelTo = (CARelation)VO.get("CARelationTo");
/* 2240 */       retInfo = CAFunctions.getCAWorkInfo(caller, caRelTo, false);
/* 2241 */       if (retInfo.isError()) {
/* 2242 */         return retInfo;
/*      */       }
/* 2244 */       VODynaBean caWorkInfoTo = (VODynaBean)retInfo.getOutputParameter("CAWorkInfoBean");
/* 2245 */       VODynaBean currAccTransferVO = VOFactory.create("CurrAccTransferVO");
/* 2246 */       currAccTransferVO.set("ValueDate", VO.get("ValueDate"));
/* 2247 */       currAccTransferVO.set("Remark", VO.get("Remark2"));
/* 2248 */       currAccTransferVO.set("Amount", VO.get("NewAmount"));
/* 2249 */       currAccTransferVO.set("CAWorkInfo", caWorkInfoTo);
/*      */       
/* 2251 */       caObject.setCallerInfo(caller);
/* 2252 */       caObject.setObjectByRelationType(caRelTo.getRELATIONTYPE());
/*      */       
/* 2254 */       retInfo = caObject.setData(currAccTransferVO, "258", null);
/* 2255 */       if (retInfo.isError()) {
/* 2256 */         return retInfo;
/*      */       }
/* 2258 */       VODynaBean caWorkInfoFrom = (VODynaBean)VO.get("CAWorkInfoFrom");
/* 2259 */       VODynaBean caWorkDetailsFrom = (VODynaBean)caWorkInfoFrom.get("CAWorkDetails");
/* 2260 */       VO.set("Subcategory", caWorkDetailsFrom.get("Subcategory"));
/*      */ 
/*      */       
/* 2263 */       ArrayList blockList = (ArrayList)caWorkInfoFrom.get("BlocksList");
/*      */ 
/*      */       
/* 2266 */       AuthzKeyItem itemAuthKey = CAUtils.CheckAuthBlocks(caller, blockList, 9, null);
/*      */       
/* 2268 */       if (itemAuthKey != null) {
/* 2269 */         String sAuthID = (String)caller.getAuthzKey(itemAuthKey);
/* 2270 */         if (sAuthID == null) {
/* 2271 */           AuthzControlItem authControlItem = itemAuthKey.getAuthControlItem();
/* 2272 */           VODynaBean VORequest = VOFactory.create("AUTHZREQUEST");
/* 2273 */           VORequest.set("C_SUBSYSTEM", itemAuthKey.getSubSystem());
/* 2274 */           VORequest.set("C_CONTROLTYPE", itemAuthKey.getControl());
/* 2275 */           VORequest.set("C_CONTROLSUBTYPE", itemAuthKey.getSubControl());
/* 2276 */           VORequest.set("C_KEY", itemAuthKey.getKey());
/* 2277 */           if (authControlItem != null) {
/* 2278 */             VORequest.set("LIV_AUTZ", new Short(authControlItem.getLevel()));
/*      */           }
/* 2280 */           IFrameworkDelegateFactory globalFactory = (IFrameworkDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.business.delegateFactory");
/* 2281 */           IAuthorization serviceAuth = globalFactory.createAuthDelegate();
/* 2282 */           retInfo = serviceAuth.RequestAndInsertAuthz(caller, VORequest);
/* 2283 */           if (retInfo.isError())
/* 2284 */             return retInfo; 
/* 2285 */           sAuthID = (String)retInfo.getOutputParameter("RETURN_AUTHZID");
/* 2286 */           if (sAuthID != null)
/* 2287 */             caller.addAuthzKey(itemAuthKey, sAuthID); 
/*      */         } 
/* 2289 */         clearingChequeRejectionObject.addAuthID(sAuthID, itemAuthKey.getKey());
/*      */       } 
/*      */ 
/*      */       
/* 2293 */       clearingChequeRejectionObject.setCallerInfo(caller);
/* 2294 */       retInfo = clearingChequeRejectionObject.setData(VO, caller.getFunction(), null);
/* 2295 */       if (retInfo.isError()) {
/* 2296 */         return retInfo;
/*      */       }
/* 2298 */       caObject.setSkipControlOnCreditUnclaimedBlock(true);
/*      */       
/* 2300 */       retInfo = deskController.addBusinessObject((IAccountingBO)caObject, -1);
/* 2301 */       if (retInfo.isError()) {
/* 2302 */         return retInfo;
/*      */       }
/*      */       
/* 2305 */       retInfo = deskController.addBusinessObject((IAccountingBO)clearingChequeRejectionObject, -1);
/* 2306 */       if (retInfo.isError()) {
/* 2307 */         return retInfo;
/*      */       }
/*      */ 
/*      */       
/* 2311 */       retInfo = deskController.RefreshDeskWithError();
/* 2312 */       if (retInfo.isError())
/* 2313 */         return retInfo; 
/* 2314 */       retInfo = deskController.ExecuteTransaction(caller, true, true);
/*      */     }
/* 2316 */     catch (BusinessException be) {
/* 2317 */       throw new ServiceException("Error: clearingChequeRejection() ", be);
/* 2318 */     } catch (DeskException de) {
/* 2319 */       throw new ServiceException("Error: clearingChequeRejection() ", de);
/* 2320 */     } catch (RemoteException re) {
/* 2321 */       throw new ServiceException("Error: clearingChequeRejection() ", re);
/*      */     } 
/*      */     
/* 2324 */     return retInfo;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo searchReversalClearingCheques(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 2330 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2332 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 2333 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 2334 */     if (searchVO == null || !searchVO.getDynaClass().getName().equals("SearchReversalClearingCheques")) {
/* 2335 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Expecting VO of type SearchReversalClearingCheques"));
/*      */     }
/*      */ 
/*      */ 
/*      */     
/* 2340 */     String inputBuffer = ObjectFunctionConfig.getObjectFunctionIN("searchReversalClearingCheques");
/* 2341 */     String outputBuffer = ObjectFunctionConfig.getObjectFunctionOUT("searchReversalClearingCheques");
/*      */ 
/*      */     
/* 2344 */     ReturnInfo retInfoCount = getReversalClearingChequesCount(caller, searchVO);
/* 2345 */     if (retInfoCount.isError()) {
/* 2346 */       return retInfoCount;
/*      */     }
/*      */     
/* 2349 */     Integer totalRowsCount = (Integer)retInfoCount.getOutputParameter("COUNT");
/*      */     
/* 2351 */     BricContainer bricCont = new BricContainer();
/*      */     
/* 2353 */     CARelation relation = (CARelation)searchVO.get("CARelation");
/*      */     
/* 2355 */     CachedRowSet crs = bricCont.createRowSet(inputBuffer, null);
/*      */     
/*      */     try {
/* 2358 */       crs.moveToInsertRow();
/* 2359 */       crs.updateString("C_STATUS", (String)searchVO.get("ChequeStatus"));
/* 2360 */       crs.updateString("C_ABI", (String)searchVO.get("Bank"));
/* 2361 */       crs.updateString("C_CAB", (String)searchVO.get("Region"));
/* 2362 */       crs.updateString("TP_ASS", (String)searchVO.get("ChequeType"));
/* 2363 */       crs.updateBigDecimal("N_ASS", (BigDecimal)searchVO.get("ChequeNumber"));
/* 2364 */       crs.updateString("N_CT_TRAE", (String)searchVO.get("DrawerRelationNo"));
/* 2365 */       crs.updateObject("DT_FROM", searchVO.get4EIS("TransactionDateFrom"));
/* 2366 */       crs.updateObject("DT_TO", searchVO.get4EIS("TransactionDateTo"));
/* 2367 */       crs.updateString("C_AG", (String)searchVO.get("AccountingBranch"));
/* 2368 */       crs.updateString("C_AG_RAPP", relation.getRELATIONBRANCH());
/* 2369 */       crs.updateString("C_TP_RAPP", relation.getRELATIONTYPE());
/* 2370 */       crs.updateString("N_RAPP", relation.getRELATIONNUMBER());
/* 2371 */       crs.updateObject("IMP_TO", searchVO.get4EIS("ChequeAmountTo"));
/* 2372 */       crs.updateObject("IMP_FROM", searchVO.get4EIS("ChequeAmountFrom"));
/* 2373 */       crs.updateObject("DT_ELAB_FROM", searchVO.get4EIS("RealizationDateFrom"));
/* 2374 */       crs.updateObject("DT_ELAB_TO", searchVO.get4EIS("RealizationDateTo"));
/* 2375 */       crs.updateObject("DT_VAL_FROM", searchVO.get4EIS("ValueDateFrom"));
/* 2376 */       crs.updateObject("DT_VAL_TO", searchVO.get4EIS("ValueDateTo"));
/*      */       
/* 2378 */       bricCont.DoPagination(inputBuffer, crs, searchVO, totalRowsCount);
/*      */       
/* 2380 */       crs.insertRow();
/* 2381 */     } catch (SQLException e) {
/* 2382 */       throw new ServiceException(" Error searchClearingCheques() " + e.getMessage(), "9000", e);
/*      */     } 
/*      */     
/* 2385 */     if (!bricCont.ExecuteQuery(caller)) {
/* 2386 */       return bricCont.getReturnInfo();
/*      */     }
/*      */     
/* 2389 */     if (!bricCont.TransferToVOList(outputBuffer, "ClearingChequesVO")) {
/* 2390 */       return bricCont.getReturnInfo();
/*      */     }
/* 2392 */     retInfo = bricCont.getReturnInfo();
/*      */     
/* 2394 */     ArrayList<VODynaBean> retVOList = bricCont.getReturnVOList("ClearingChequesVO");
/*      */     
/* 2396 */     if (retVOList != null) {
/*      */       
/* 2398 */       LinkedHashMap regionMap = RegionForLocalBankData.getRegionForLocalBankList(caller);
/*      */       
/* 2400 */       for (int i = 0; i < retVOList.size(); i++) {
/* 2401 */         VODynaBean chequeVO = retVOList.get(i);
/*      */         
/* 2403 */         String relationBranch = (String)chequeVO.get("RelationBranch");
/* 2404 */         String relationType = (String)chequeVO.get("RelationType");
/* 2405 */         String relationNumber = (String)chequeVO.get("RelationNumber");
/* 2406 */         CARelation caRel = new CARelation(relationBranch, relationType, relationNumber);
/* 2407 */         chequeVO.set("CARelation", caRel);
/*      */         
/* 2409 */         String currency = (String)chequeVO.get("Currency");
/* 2410 */         CurrencyItem currencyItem = CurrencyData.GetCurrencyItem(caller, currency);
/* 2411 */         chequeVO.setDescription("Currency", (currencyItem != null) ? currencyItem.getDescription() : currency);
/*      */         
/* 2413 */         ElsagAmount amount = (ElsagAmount)chequeVO.get("Amount");
/* 2414 */         if (currencyItem != null && currencyItem.getDecimals() != CurrencyData.GetLocalDecimals()) {
/* 2415 */           amount.changeValueByNewDecimal(currencyItem.getDecimals());
/*      */         }
/*      */         
/* 2418 */         String bank = (String)chequeVO.get("Bank");
/* 2419 */         String region = (String)chequeVO.get("Region");
/* 2420 */         String chequeType = (String)chequeVO.get("ChequeType");
/* 2421 */         BigDecimal chequeNumber = (BigDecimal)chequeVO.get("ChequeNumber");
/* 2422 */         String drawerRelationNo = (String)chequeVO.get("DrawerRelationNo");
/*      */         
/* 2424 */         StringBuffer trackingNumber = new StringBuffer("");
/* 2425 */         trackingNumber.append(StringUtils.fillChars(bank, ' ', 5, false));
/* 2426 */         trackingNumber.append(StringUtils.fillChars(region, ' ', 5, false));
/* 2427 */         trackingNumber.append(StringUtils.fillChars(chequeType, ' ', 1, false));
/* 2428 */         trackingNumber.append(StringUtils.fillChars(chequeNumber.toString(), ' ', 10, true));
/* 2429 */         trackingNumber.append(StringUtils.fillChars(drawerRelationNo, ' ', 8, false));
/*      */         
/* 2431 */         chequeVO.set("TrackingNumber", trackingNumber.toString());
/*      */         
/* 2433 */         CorrespBanksItem cbiItem = CorrespondentBankData.getCorrespBankItem(caller, bank);
/* 2434 */         chequeVO.setDescription("Bank", (cbiItem != null) ? cbiItem.getLabel() : bank);
/*      */         
/* 2436 */         String regionDesc = region;
/* 2437 */         if (cbiItem != null && "1".equals(cbiItem.getNaz())) {
/* 2438 */           LabelValueBean lvb = (LabelValueBean)regionMap.get(region);
/* 2439 */           if (lvb != null)
/* 2440 */             regionDesc = lvb.getLabel(); 
/*      */         } 
/* 2442 */         chequeVO.setDescription("Region", regionDesc);
/*      */       } 
/*      */     } 
/*      */     
/* 2446 */     retInfo.setOutputParameter("RETURNVOLIST", retVOList);
/*      */     
/* 2448 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo getReversalClearingChequesCount(CallerInfo caller, VODynaBean searchVO) throws ServiceException {
/* 2452 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2454 */     String searchKey = "BCHQ.063";
/* 2455 */     String returnKey = "COUNT.550";
/*      */     
/* 2457 */     BricContainer bricContainer = new BricContainer();
/* 2458 */     CachedRowSet crs = bricContainer.createRowSet(searchKey, null);
/*      */     
/* 2460 */     CARelation relation = (CARelation)searchVO.get("CARelation");
/*      */     
/*      */     try {
/* 2463 */       crs.moveToInsertRow();
/* 2464 */       crs.updateString("C_STATUS", (String)searchVO.get("ChequeStatus"));
/* 2465 */       crs.updateString("C_ABI", (String)searchVO.get("Bank"));
/* 2466 */       crs.updateString("C_CAB", (String)searchVO.get("Region"));
/* 2467 */       crs.updateString("TP_ASS", (String)searchVO.get("ChequeType"));
/* 2468 */       crs.updateBigDecimal("N_ASS", (BigDecimal)searchVO.get("ChequeNumber"));
/* 2469 */       crs.updateString("N_CT_TRAE", (String)searchVO.get("DrawerRelationNo"));
/* 2470 */       crs.updateObject("DT_FROM", searchVO.get4EIS("TransactionDateFrom"));
/* 2471 */       crs.updateObject("DT_TO", searchVO.get4EIS("TransactionDateTo"));
/* 2472 */       crs.updateString("C_AG", (String)searchVO.get("AccountingBranch"));
/* 2473 */       crs.updateString("C_AG_RAPP", relation.getRELATIONBRANCH());
/* 2474 */       crs.updateString("C_TP_RAPP", relation.getRELATIONTYPE());
/* 2475 */       crs.updateString("N_RAPP", relation.getRELATIONNUMBER());
/* 2476 */       crs.updateObject("IMP_TO", searchVO.get4EIS("ChequeAmountTo"));
/* 2477 */       crs.updateObject("IMP_FROM", searchVO.get4EIS("ChequeAmountFrom"));
/* 2478 */       crs.updateObject("DT_ELAB_FROM", searchVO.get4EIS("RealizationDateFrom"));
/* 2479 */       crs.updateObject("DT_ELAB_TO", searchVO.get4EIS("RealizationDateTo"));
/* 2480 */       crs.updateObject("DT_VAL_FROM", searchVO.get4EIS("ValueDateFrom"));
/* 2481 */       crs.updateObject("DT_VAL_TO", searchVO.get4EIS("ValueDateTo"));
/* 2482 */       crs.insertRow();
/* 2483 */     } catch (SQLException e) {
/* 2484 */       throw new ServiceException(" Error SQL getReversalClearingChequesCount(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 2487 */     if (!bricContainer.ExecuteQuery(caller)) {
/* 2488 */       return bricContainer.getReturnInfo();
/*      */     }
/*      */     
/* 2491 */     CachedRowSet retCachedRowSet = bricContainer.getOutputRowSet(returnKey);
/*      */     
/* 2493 */     if (retCachedRowSet != null) {
/*      */       try {
/* 2495 */         retCachedRowSet.moveToCurrentRow();
/* 2496 */         retCachedRowSet.beforeFirst();
/* 2497 */         retCachedRowSet.next();
/*      */         
/* 2499 */         Integer count = Integer.valueOf(retCachedRowSet.getInt("COUNT"));
/* 2500 */         retInfo.setOutputParameter("COUNT", count);
/* 2501 */       } catch (SQLException e) {
/* 2502 */         throw new ServiceException(" Error getReversalClearingChequesCount() ", e);
/*      */       } 
/*      */     }
/* 2505 */     return retInfo.setOK();
/*      */   }
/*      */ 
/*      */   
/*      */   public ReturnInfo updateValueDate(CallerInfo caller, VODynaBean updateVO) throws ServiceException {
/* 2510 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2512 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 2513 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 2514 */     if (updateVO == null || !updateVO.getDynaClass().getName().equals("ClearingChequesVO")) {
/* 2515 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Expecting VO of type ClearingChequesVO"));
/*      */     }
/*      */     
/* 2518 */     BricContainer bricContainer = new BricContainer();
/* 2519 */     ElsagDate newValueDate = (ElsagDate)updateVO.get("ValueDate");
/* 2520 */     if (newValueDate.compareTo(new ElsagDate(caller.getAccountingDate())) != 0) {
/* 2521 */       retInfo = prepareUpdateValueDate(caller, updateVO);
/* 2522 */       if (retInfo.isError())
/* 2523 */         return retInfo; 
/* 2524 */       bricContainer.AddLinkedHashMap((LinkedHashMap)retInfo.removeOutputParameter("CONTAINER_OUT"));
/*      */       
/* 2526 */       if (!bricContainer.ExecTransaction(caller)) {
/* 2527 */         return bricContainer.getReturnInfo();
/*      */       }
/* 2529 */       retInfo = bricContainer.getReturnInfo();
/*      */     }
/*      */     else {
/*      */       
/* 2533 */       CARelation caRel = (CARelation)updateVO.get("CARelation");
/* 2534 */       if (!StringUtils.isEmpty(caRel.getRELATIONNUMBER())) {
/*      */         
/*      */         try {
/* 2537 */           ChequeValueDateObject chequeObj = new ChequeValueDateObject();
/* 2538 */           chequeObj.setCallerInfo(caller);
/*      */           
/* 2540 */           retInfo = chequeObj.setData(updateVO, null, null);
/* 2541 */           if (retInfo.isError()) {
/* 2542 */             return retInfo;
/*      */           }
/*      */           
/* 2545 */           retInfo = CAFunctions.getCAWorkInfo(caller, caRel, false);
/* 2546 */           if (retInfo.isError()) {
/* 2547 */             return retInfo;
/*      */           }
/* 2549 */           VODynaBean caWorkInfoTo = (VODynaBean)retInfo.getOutputParameter("CAWorkInfoBean");
/*      */           
/* 2551 */           VODynaBean currAccTransferVO = VOFactory.create("CurrAccTransferVO");
/* 2552 */           currAccTransferVO.set("ValueDate", newValueDate);
/* 2553 */           currAccTransferVO.set("Remark", "Payment Cheque no:" + updateVO.get("ChequeNumber"));
/* 2554 */           currAccTransferVO.set("CAWorkInfo", caWorkInfoTo);
/*      */           
/* 2556 */           RejectClearingChequeConfigItem chequesConfigItem = ChequesFunctions.getRejectionChequeConfiguration(caller);
/* 2557 */           if (chequesConfigItem == null)
/* 2558 */             return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1050", "Wrong configuration of ICRR1E03")); 
/* 2559 */           currAccTransferVO.set("CustomTransCode", chequesConfigItem.getTransactionCodeCredit());
/*      */           
/* 2561 */           CurrentAccountObject caObject = new CurrentAccountObject(Role.Credit);
/* 2562 */           caObject.setSkipControlOnCreditUnclaimedBlock(true);
/* 2563 */           caObject.setCallerInfo(caller);
/* 2564 */           caObject.setObjectByRelationType(caRel.getRELATIONTYPE());
/*      */           
/* 2566 */           retInfo = caObject.setData(currAccTransferVO, "258", null);
/* 2567 */           if (retInfo.isError()) {
/* 2568 */             return retInfo;
/*      */           }
/* 2570 */           IDeskDelegateFactory iDeskClassFactory = (IDeskDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.desk.business.delegateFactory");
/* 2571 */           IDeskBusiness deskBusiness = iDeskClassFactory.createDesk();
/*      */           
/* 2573 */           Handle deskHandle = deskBusiness.GetHandle();
/* 2574 */           if (deskHandle == null) {
/* 2575 */             throw new ServiceException("Error: DeskHandle is Null");
/*      */           }
/* 2577 */           DeskController deskController = (DeskController)deskHandle.getEJBObject();
/* 2578 */           if (deskController == null)
/* 2579 */             throw new ServiceException("Error: DeskController is Null"); 
/* 2580 */           deskController.setCallerInfo(caller);
/*      */           
/* 2582 */           retInfo = deskController.addBusinessObject((IAccountingBO)chequeObj, -1);
/* 2583 */           if (retInfo.isError()) {
/* 2584 */             return retInfo;
/*      */           }
/* 2586 */           retInfo = deskController.addBusinessObject((IAccountingBO)caObject, -1);
/* 2587 */           if (retInfo.isError()) {
/* 2588 */             return retInfo;
/*      */           }
/* 2590 */           retInfo = deskController.RefreshDeskWithError();
/* 2591 */           if (retInfo.isError()) {
/* 2592 */             return retInfo;
/*      */           }
/* 2594 */           retInfo = deskController.ExecuteTransaction(caller, true, true);
/* 2595 */           if (retInfo.isError()) {
/* 2596 */             return retInfo;
/*      */           }
/*      */         }
/* 2599 */         catch (BusinessException e) {
/* 2600 */           throw new ServiceException("Error updateValueDate", e);
/* 2601 */         } catch (DeskException e) {
/* 2602 */           throw new ServiceException("Error updateValueDate", e);
/* 2603 */         } catch (RemoteException e) {
/* 2604 */           throw new ServiceException("Error updateValueDate", e);
/*      */         } 
/*      */       }
/*      */     } 
/*      */     
/* 2609 */     return retInfo;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo reversalClearingCheque(CallerInfo caller, VODynaBean VO) throws ServiceException {
/* 2615 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2617 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 2618 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 2620 */     if (!VO.getDynaClass().getName().equals("ClearingChequesVO")) {
/* 2621 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/* 2623 */     CurrentAccountObject caObject = new CurrentAccountObject(Role.Credit);
/* 2624 */     ReversalClearingChequeObject reversalClearingChequeObject = new ReversalClearingChequeObject(Role.Debit);
/*      */     
/*      */     try {
/* 2627 */       IDeskDelegateFactory iDeskClassFactory = (IDeskDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.desk.business.delegateFactory");
/* 2628 */       IDeskBusiness deskBusiness = iDeskClassFactory.createDesk();
/* 2629 */       Handle deskHandle = deskBusiness.GetHandle();
/*      */       
/* 2631 */       DeskController deskController = (DeskController)deskHandle.getEJBObject();
/*      */       
/* 2633 */       if (deskController == null)
/* 2634 */         throw new ServiceException("Error: DeskController is Null"); 
/* 2635 */       deskController.setCallerInfo(caller);
/*      */       
/* 2637 */       CARelation caRelTo = (CARelation)VO.get("CARelation");
/* 2638 */       VODynaBean caWorkInfoTo = (VODynaBean)VO.get("CAWorkInfoBean");
/* 2639 */       VODynaBean currAccTransferVO = VOFactory.create("CurrAccTransferVO");
/* 2640 */       currAccTransferVO.set("ValueDate", new ElsagDate(new Date()));
/* 2641 */       currAccTransferVO.set("Remark", VO.get("RemarkCredit"));
/* 2642 */       currAccTransferVO.set("Amount", VO.get("Amount"));
/* 2643 */       currAccTransferVO.set("CAWorkInfo", caWorkInfoTo);
/* 2644 */       currAccTransferVO.set("CustomTransCode", VO.get("TransactionCodeCredit"));
/*      */       
/* 2646 */       caObject.setCallerInfo(caller);
/* 2647 */       caObject.setObjectByRelationType(caRelTo.getRELATIONTYPE());
/* 2648 */       caObject.setFixedRemark(true);
/*      */ 
/*      */       
/* 2651 */       ArrayList blockList = (ArrayList)caWorkInfoTo.get("BlocksList");
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */       
/* 2658 */       AuthzKeyItem itemAuthKey = CAUtils.CheckAuthBlocks(caller, blockList, 9, Role.Debit);
/*      */       
/* 2660 */       if (itemAuthKey != null) {
/* 2661 */         String sAuthID = (String)caller.getAuthzKey(itemAuthKey);
/* 2662 */         if (sAuthID == null) {
/* 2663 */           AuthzControlItem authControlItem = itemAuthKey.getAuthControlItem();
/* 2664 */           VODynaBean VORequest = VOFactory.create("AUTHZREQUEST");
/* 2665 */           VORequest.set("C_SUBSYSTEM", itemAuthKey.getSubSystem());
/* 2666 */           VORequest.set("C_CONTROLTYPE", itemAuthKey.getControl());
/* 2667 */           VORequest.set("C_CONTROLSUBTYPE", itemAuthKey.getSubControl());
/* 2668 */           VORequest.set("C_KEY", itemAuthKey.getKey());
/* 2669 */           if (authControlItem != null) {
/* 2670 */             VORequest.set("LIV_AUTZ", new Short(authControlItem.getLevel()));
/*      */           }
/* 2672 */           IFrameworkDelegateFactory globalFactory = (IFrameworkDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.business.delegateFactory");
/* 2673 */           IAuthorization serviceAuth = globalFactory.createAuthDelegate();
/* 2674 */           retInfo = serviceAuth.RequestAndInsertAuthz(caller, VORequest);
/* 2675 */           if (retInfo.isError())
/* 2676 */             return retInfo; 
/* 2677 */           sAuthID = (String)retInfo.getOutputParameter("RETURN_AUTHZID");
/* 2678 */           if (sAuthID != null)
/* 2679 */             caller.addAuthzKey(itemAuthKey, sAuthID); 
/*      */         } 
/* 2681 */         reversalClearingChequeObject.addAuthID(sAuthID, itemAuthKey.getKey());
/*      */       } 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */       
/* 2689 */       AuthzKeyItem itemAuthKeyCredit = CAUtils.CheckAuthBlocks(caller, blockList, 9, Role.Credit);
/*      */       
/* 2691 */       if (itemAuthKeyCredit != null) {
/* 2692 */         String sAuthID = (String)caller.getAuthzKey(itemAuthKeyCredit);
/* 2693 */         if (sAuthID == null) {
/* 2694 */           AuthzControlItem authControlItem = itemAuthKeyCredit.getAuthControlItem();
/* 2695 */           VODynaBean VORequestCredit = VOFactory.create("AUTHZREQUEST");
/* 2696 */           VORequestCredit.set("C_SUBSYSTEM", itemAuthKeyCredit.getSubSystem());
/* 2697 */           VORequestCredit.set("C_CONTROLTYPE", itemAuthKeyCredit.getControl());
/* 2698 */           VORequestCredit.set("C_CONTROLSUBTYPE", itemAuthKeyCredit.getSubControl());
/* 2699 */           VORequestCredit.set("C_KEY", itemAuthKeyCredit.getKey());
/* 2700 */           if (authControlItem != null) {
/* 2701 */             VORequestCredit.set("LIV_AUTZ", new Short(authControlItem.getLevel()));
/*      */           }
/* 2703 */           IFrameworkDelegateFactory globalFactory = (IFrameworkDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.business.delegateFactory");
/* 2704 */           IAuthorization serviceAuth = globalFactory.createAuthDelegate();
/* 2705 */           retInfo = serviceAuth.RequestAndInsertAuthz(caller, VORequestCredit);
/* 2706 */           if (retInfo.isError())
/* 2707 */             return retInfo; 
/* 2708 */           sAuthID = (String)retInfo.getOutputParameter("RETURN_AUTHZID");
/* 2709 */           if (sAuthID != null)
/* 2710 */             caller.addAuthzKey(itemAuthKeyCredit, sAuthID); 
/*      */         } 
/* 2712 */         caObject.addAuthID(sAuthID, itemAuthKeyCredit.getKey());
/*      */       } 
/*      */ 
/*      */       
/* 2716 */       retInfo = caObject.setData(currAccTransferVO, "258", null);
/* 2717 */       if (retInfo.isError()) {
/* 2718 */         return retInfo;
/*      */       }
/*      */       
/* 2721 */       caObject.setSkipControlOnCreditUnclaimedBlock(true);
/*      */       
/* 2723 */       retInfo = deskController.addBusinessObject((IAccountingBO)caObject, -1);
/* 2724 */       if (retInfo.isError()) {
/* 2725 */         return retInfo;
/*      */       }
/*      */       
/* 2728 */       reversalClearingChequeObject.setCallerInfo(caller);
/* 2729 */       retInfo = reversalClearingChequeObject.setData(VO, caller.getFunction(), null);
/* 2730 */       if (retInfo.isError()) {
/* 2731 */         return retInfo;
/*      */       }
/* 2733 */       retInfo = deskController.addBusinessObject((IAccountingBO)reversalClearingChequeObject, -1);
/* 2734 */       if (retInfo.isError()) {
/* 2735 */         return retInfo;
/*      */       }
/*      */ 
/*      */       
/* 2739 */       retInfo = deskController.RefreshDeskWithError();
/* 2740 */       if (retInfo.isError())
/* 2741 */         return retInfo; 
/* 2742 */       retInfo = deskController.ExecuteTransaction(caller, true, true);
/*      */ 
/*      */       
/* 2745 */       if (retInfo.getReturnCode().equals("3")) {
/* 2746 */         ArrayList<VODynaBean> ovavList = (ArrayList)retInfo.removeOutputParameter("RESULT_OVAVLIST");
/* 2747 */         itemAuthKey = (AuthzKeyItem)retInfo.removeOutputParameter("AUTHNEED");
/* 2748 */         if (ovavList != null && !ovavList.isEmpty()) {
/* 2749 */           VODynaBean authBean = ovavList.get(0);
/*      */           
/* 2751 */           if (itemAuthKey == null) {
/* 2752 */             itemAuthKey = (AuthzKeyItem)authBean.get("AuthzKeyItem");
/*      */           }
/* 2754 */           if (caller.getAuthzKey(itemAuthKey) == null) {
/* 2755 */             VODynaBean VORequest = VOFactory.create("AUTHZREQUEST");
/* 2756 */             VORequest.set("C_SUBSYSTEM", itemAuthKey.getSubSystem());
/* 2757 */             VORequest.set("C_CONTROLTYPE", itemAuthKey.getControl());
/* 2758 */             VORequest.set("C_CONTROLSUBTYPE", itemAuthKey.getSubControl());
/* 2759 */             VORequest.set("C_KEY", itemAuthKey.getKey());
/* 2760 */             VORequest.set("LIV_AUTZ", authBean.get("LIV_AUTZ"));
/*      */             
/* 2762 */             IFrameworkDelegateFactory globalFactory = (IFrameworkDelegateFactory)ClassFactory.createFactoryClass("it.elsag.common.business.delegateFactory");
/* 2763 */             IAuthorization serviceAuth = globalFactory.createAuthDelegate();
/* 2764 */             retInfo = serviceAuth.RequestAndInsertAuthz(caller, VORequest);
/* 2765 */             if (retInfo.isError())
/* 2766 */               return retInfo; 
/* 2767 */             String sAuthID = (String)retInfo.getOutputParameter("RETURN_AUTHZID");
/* 2768 */             if (sAuthID != null) {
/* 2769 */               caller.addAuthzKey(itemAuthKey, sAuthID);
/*      */               
/* 2771 */               retInfo = deskController.ExecuteTransaction(caller, true, true);
/*      */             }
/*      */           
/*      */           } 
/*      */         } 
/*      */       } 
/* 2777 */     } catch (BusinessException be) {
/* 2778 */       throw new ServiceException("Error: reversalClearingCheque() ", be);
/* 2779 */     } catch (DeskException de) {
/* 2780 */       throw new ServiceException("Error: reversalClearingCheque() ", de);
/* 2781 */     } catch (RemoteException re) {
/* 2782 */       throw new ServiceException("Error: reversalClearingCheque() ", re);
/*      */     } 
/*      */     
/* 2785 */     return retInfo;
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getRejectionChequeConfiguration(CallerInfo caller) throws ServiceException {
/* 2796 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2798 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 2799 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 2801 */     RejectClearingChequeConfigItem rejectClearingChequeConfigItem = ChequesFunctions.getRejectionChequeConfiguration(caller);
/* 2802 */     if (rejectClearingChequeConfigItem == null) {
/* 2803 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1050", "Wrong configuration of ICRR1E03"));
/*      */     }
/* 2805 */     retInfo.setOutputParameter("RETURNITEM", rejectClearingChequeConfigItem);
/* 2806 */     return retInfo.setOK();
/*      */   }
/*      */ 
/*      */   
/*      */   public ReturnInfo bulkUpdateValueDate(CallerInfo caller, ArrayList<VODynaBean> updateVOList) throws ServiceException {
/* 2811 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2813 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 2814 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 2815 */     if (updateVOList == null || updateVOList.size() == 0) {
/* 2816 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Update List is empty"));
/*      */     }
/*      */     
/* 2819 */     AuthzKeyItem itemAuthKey = new AuthzKeyItem("SB20", "06", "29", "");
/* 2820 */     ReturnInfo retAuth = AuthUtil.VerifyAuthorizationNeed(caller, itemAuthKey);
/* 2821 */     String sAuthID = (String)retAuth.getOutputParameter("OBJECTAUTHID");
/* 2822 */     if (retAuth.isError()) {
/* 2823 */       return retAuth;
/*      */     }
/* 2825 */     BricContainer container = new BricContainer();
/*      */     
/* 2827 */     for (VODynaBean updateVO : updateVOList) {
/* 2828 */       retInfo = prepareUpdateValueDate(caller, updateVO);
/* 2829 */       if (retInfo.isError())
/* 2830 */         return retInfo; 
/* 2831 */       container.AddLinkedHashMap((LinkedHashMap)retInfo.removeOutputParameter("CONTAINER_OUT"));
/*      */     } 
/*      */     
/* 2834 */     if (sAuthID != null) {
/* 2835 */       retAuth = AuthUtil.PrepareAuthorizationBuffers(caller, sAuthID);
/* 2836 */       if (retAuth.isError())
/* 2837 */         return retAuth; 
/* 2838 */       container.AddLinkedHashMap((LinkedHashMap)retAuth.removeOutputParameter("RETURNROWSETS"));
/*      */     } 
/*      */     
/* 2841 */     if (!container.ExecTransaction(caller)) {
/* 2842 */       return container.getReturnInfo();
/*      */     }
/* 2844 */     retInfo = container.getReturnInfo();
/* 2845 */     return retInfo;
/*      */   }
/*      */   
/*      */   public ReturnInfo prepareUpdateValueDate(CallerInfo caller, VODynaBean updateVO) throws ServiceException {
/* 2849 */     ReturnInfo retInfo = new ReturnInfo();
/*      */     
/* 2851 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null)
/* 2852 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null)); 
/* 2853 */     if (updateVO == null || !updateVO.getDynaClass().getName().equals("ClearingChequesVO")) {
/* 2854 */       return retInfo.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Expecting VO of type ClearingChequesVO"));
/*      */     }
/* 2856 */     String keyIn = "BCHQ.020";
/*      */     
/* 2858 */     BricContainer bricContainer = new BricContainer();
/* 2859 */     CachedRowSet crs = bricContainer.createRowSet(keyIn, null);
/*      */     try {
/* 2861 */       crs.moveToInsertRow();
/* 2862 */       crs.updateString("C_ABI", (String)updateVO.get("Bank"));
/* 2863 */       crs.updateString("C_CAB", (String)updateVO.get("Region"));
/* 2864 */       crs.updateString("TP_ASS", (String)updateVO.get("ChequeType"));
/* 2865 */       crs.updateBigDecimal("N_ASS", (BigDecimal)updateVO.get("ChequeNumber"));
/* 2866 */       crs.updateString("N_CT_TRAE", (String)updateVO.get("DrawerRelationNo"));
/* 2867 */       crs.updateObject("DT_VAL", updateVO.get4EIS("ValueDate"));
/* 2868 */       crs.insertRow();
/* 2869 */     } catch (SQLException e) {
/* 2870 */       throw new ServiceException(" Error SQL prepareUpdateValueDate(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 2873 */     retInfo.setOutputParameter("CONTAINER_OUT", bricContainer.getInput());
/* 2874 */     return retInfo.setOK();
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getLinkPressNameList(CallerInfo caller) throws ServiceException {
/* 2880 */     return ChequesFunctions.GetLinkPressNameList(caller);
/*      */   }
/*      */   
/*      */   public ReturnInfo getLinkPressPrintingTypesList(CallerInfo caller) throws ServiceException {
/* 2884 */     return ChequesFunctions.GetLinkPressPrintingTypesList(caller);
/*      */   }
/*      */   
/*      */   public ReturnInfo insertLinkPress(CallerInfo caller, VODynaBean linkPressVO) throws ServiceException {
/* 2888 */     ReturnInfo ret = new ReturnInfo();
/* 2889 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 2890 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 2892 */     if (linkPressVO == null || !linkPressVO.getDynaClass().getName().equals("LinkPressVO")) {
/* 2893 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/*      */     
/* 2896 */     AuthzKeyItem itemAuthKey = new AuthzKeyItem("SB20", "27", "10", "");
/* 2897 */     ReturnInfo retAuth = AuthUtil.VerifyAuthorizationNeed(caller, itemAuthKey);
/* 2898 */     if (!retAuth.getReturnCode().equals("0")) {
/* 2899 */       return retAuth;
/*      */     }
/* 2901 */     String sAuthID = (String)retAuth.getOutputParameter("OBJECTAUTHID");
/*      */     
/* 2903 */     BricContainer container = new BricContainer();
/* 2904 */     CachedRowSet crs = null;
/*      */ 
/*      */     
/* 2907 */     String userAuth = "";
/* 2908 */     if (sAuthID != null) {
/* 2909 */       retAuth = AuthUtil.PrepareAuthorizationBuffers(caller, sAuthID);
/* 2910 */       if (retAuth.isError())
/* 2911 */         return retAuth; 
/* 2912 */       container.AddLinkedHashMap((LinkedHashMap)retAuth.removeOutputParameter("RETURNROWSETS"));
/* 2913 */       VODynaBean VOAuth = (VODynaBean)retAuth.removeOutputParameter("RETURNVO");
/* 2914 */       if (VOAuth != null) {
/* 2915 */         userAuth = (String)VOAuth.get("C_AUTHZCODE");
/*      */       }
/*      */     } 
/*      */     
/* 2919 */     crs = container.createRowSet("BCHQ.022", "0002");
/*      */     try {
/* 2921 */       CARelation caRel = (CARelation)linkPressVO.get("CurrentAccount");
/*      */       
/* 2923 */       crs.moveToInsertRow();
/* 2924 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 2925 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/* 2926 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/* 2927 */       crs.updateString("C_TP_CRNT", (String)linkPressVO.get("ChequeBookType"));
/* 2928 */       crs.updateString("C_LNK_PRSS", (String)linkPressVO.get("PressName"));
/* 2929 */       crs.updateString("C_TP_PRNT", (String)linkPressVO.get("PrintingType"));
/* 2930 */       crs.updateString("C_MATR_AUTZ", userAuth);
/* 2931 */       crs.insertRow();
/* 2932 */     } catch (SQLException e) {
/* 2933 */       throw new ServiceException("Error SQL  insertLinkPress(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 2936 */     if (!container.ExecTransaction(caller)) {
/* 2937 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 2940 */     ret = container.getReturnInfo();
/* 2941 */     return ret;
/*      */   }
/*      */   
/*      */   public ReturnInfo amendmentLinkPress(CallerInfo caller, VODynaBean linkPressVO) throws ServiceException {
/* 2945 */     ReturnInfo ret = new ReturnInfo();
/* 2946 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 2947 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 2949 */     if (linkPressVO == null || !linkPressVO.getDynaClass().getName().equals("LinkPressVO")) {
/* 2950 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", null));
/*      */     }
/*      */     
/* 2953 */     AuthzKeyItem itemAuthKey = new AuthzKeyItem("SB20", "27", "11", "");
/* 2954 */     ReturnInfo retAuth = AuthUtil.VerifyAuthorizationNeed(caller, itemAuthKey);
/* 2955 */     if (!retAuth.getReturnCode().equals("0")) {
/* 2956 */       return retAuth;
/*      */     }
/* 2958 */     String sAuthID = (String)retAuth.getOutputParameter("OBJECTAUTHID");
/*      */     
/* 2960 */     BricContainer container = new BricContainer();
/* 2961 */     CachedRowSet crs = null;
/*      */ 
/*      */     
/* 2964 */     String userAuth = "";
/* 2965 */     if (sAuthID != null) {
/* 2966 */       retAuth = AuthUtil.PrepareAuthorizationBuffers(caller, sAuthID);
/* 2967 */       if (retAuth.isError())
/* 2968 */         return retAuth; 
/* 2969 */       container.AddLinkedHashMap((LinkedHashMap)retAuth.removeOutputParameter("RETURNROWSETS"));
/* 2970 */       VODynaBean VOAuth = (VODynaBean)retAuth.removeOutputParameter("RETURNVO");
/* 2971 */       if (VOAuth != null) {
/* 2972 */         userAuth = (String)VOAuth.get("C_AUTHZCODE");
/*      */       }
/*      */     } 
/*      */     
/* 2976 */     crs = container.createRowSet("BCHQ.023", "0002");
/*      */     try {
/* 2978 */       CARelation caRel = (CARelation)linkPressVO.get("CurrentAccount");
/*      */       
/* 2980 */       crs.moveToInsertRow();
/* 2981 */       crs.updateString("C_AG", caRel.getRELATIONBRANCH());
/* 2982 */       crs.updateString("C_TP_RAPP", caRel.getRELATIONTYPE());
/* 2983 */       crs.updateString("N_RAPP", caRel.getRELATIONNUMBER());
/* 2984 */       crs.updateString("C_TP_CRNT", (String)linkPressVO.get("ChequeBookType"));
/* 2985 */       crs.updateString("C_LNK_PRSS", (String)linkPressVO.get("PressName"));
/* 2986 */       crs.updateString("C_TP_PRNT", (String)linkPressVO.get("PrintingType"));
/* 2987 */       crs.updateString("C_MATR_AUTZ", userAuth);
/* 2988 */       crs.insertRow();
/* 2989 */     } catch (SQLException e) {
/* 2990 */       throw new ServiceException("Error SQL  amendmentLinkPress(): " + e.getMessage(), "9000");
/*      */     } 
/*      */     
/* 2993 */     if (!container.ExecTransaction(caller)) {
/* 2994 */       return container.getReturnInfo();
/*      */     }
/*      */     
/* 2997 */     ret = container.getReturnInfo();
/* 2998 */     return ret;
/*      */   }
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo searchLinkPress(CallerInfo caller, CARelation caRel) throws ServiceException {
/* 3004 */     return ChequesFunctions.searchLinkPress(caller, caRel);
/*      */   }
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */ 
/*      */   
/*      */   public ReturnInfo getNumberOfChequesByStatus(CallerInfo caller, String branch, String status) throws ServiceException {
/* 3048 */     ReturnInfo ret = new ReturnInfo();
/*      */     
/* 3050 */     if (caller == null || StringUtils.isEmpty(caller.getUser()) || caller.getAccountingDate() == null) {
/* 3051 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "5001", null));
/*      */     }
/* 3053 */     if (StringUtils.isEmpty(branch) || StringUtils.isEmpty(status)) {
/* 3054 */       return ret.addError(new ErrorItem("0", "SRV_BUSINESS", "1107", "Empty parameter(s) received in getNumberOfChequesByStatus method"));
/*      */     }
/* 3056 */     BricContainer bricContainer = new BricContainer();
/* 3057 */     CachedRowSet crs = bricContainer.createRowSet("CHEQ.053", null);
/*      */     try {
/* 3059 */       crs.moveToInsertRow();
/* 3060 */       crs.updateObject("C_AG", branch);
/* 3061 */       crs.updateString("C_ST", status);
/* 3062 */       crs.insertRow();
/* 3063 */     } catch (SQLException e) {
/* 3064 */       throw new ServiceException(" Error in getNumberOfChequesByStatus(): " + e.getMessage(), e);
/*      */     } 
/*      */     
/* 3067 */     if (!bricContainer.ExecuteQuery(caller)) {
/* 3068 */       return bricContainer.getReturnInfo();
/*      */     }
/* 3070 */     ret = bricContainer.getReturnInfo();
/*      */     
/* 3072 */     CachedRowSet crsResult = bricContainer.getOutputRowSet("CHEQ.553");
/* 3073 */     if (crsResult != null) {
/*      */       try {
/* 3075 */         crsResult.moveToCurrentRow();
/* 3076 */         crsResult.beforeFirst();
/* 3077 */         crsResult.next();
/*      */         
/* 3079 */         Integer count = Integer.valueOf(crsResult.getInt("COUNT"));
/* 3080 */         ret.setOutputParameter("COUNT", count);
/*      */       }
/* 3082 */       catch (SQLException e) {
/* 3083 */         throw new ServiceException(" Error getNumberOfChequesByStatus() ", e);
/*      */       } 
/*      */     }
/* 3086 */     return ret;
/*      */   }
/*      */ }


/* Location:              F:\SAMADecompileRBSCode\RbsEAR (1).zip!\RbsChequesEJB.jar!\it\elsag\rbs\cheques\ejb\facade\ChequesControllerBean.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */