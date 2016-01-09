package org.igov.service.controller;

import com.google.common.base.Optional;
import io.swagger.annotations.*;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.apache.commons.lang3.RandomStringUtils;
import org.igov.activiti.bp.BpService;
import org.igov.io.GeneralConfig;
import org.igov.model.*;
import org.igov.model.core.EntityDao;
import org.igov.service.interceptor.exception.ActivitiRestException;
import org.igov.service.interceptor.exception.CRCInvalidException;
import org.igov.util.convert.JsonRestUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@Api(tags = { "SubjectMessageController" }, description = "Сообщения субьектов")
@RequestMapping(value = "/messages")
public class SubjectMessageController {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectMessageController.class);

    @Autowired
    private HistoryEvent_ServiceDao historyEventServiceDao;
    @Autowired
    private SubjectMessagesDao subjectMessagesDao;
    @Autowired
    @Qualifier("subjectMessageTypeDao")
    private EntityDao<SubjectMessageType> subjectMessageTypeDao;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private GeneralConfig generalConfig;
    @Autowired
    private BpService bpService;
    @Autowired
    private SubjectHumanDao subjectHumanDao;
    @Autowired
    private SubjectContactDao subjectContactDao;
    @Autowired
    private SubjectDao subjectDao;
    @Autowired
    private SubjectContactTypeDao subjectContactTypeDao;
    
    /**
     * Сохранение сообщения
     * @param sHead Строка-заглавие сообщения
     * @param sBody Строка-тело сообщения
     * @param sMail Строка электронного адреса автора //опционально
     * @param sContacts Строка контактов автора //опционально
     * @param sData Строка дополнительных данных автора //опционально
     * @param nID_SubjectMessageType ИД-номер типа сообщения  //опционально (по умолчанию == 0)
     */
    @ApiOperation(value = "Сохранение сообщение ", notes = "##### SubjectMessageController - Сообщения субьектов. Сохранение сообщения #####\n\n"
	        + "HTTP Context: http://server:port/wf/service/messages/setMessage\n\n\n"
	        + "- nID_SubjectMessageType: nID;sName;sDescription 0;ServiceNeed;Просьба добавить услугу 1;ServiceFeedback;Отзыв о услуге\n\n\n"
	        + "При заданных параметрах sID_Order или nID_Protected с/без nID_Server и sID_Rate - обновляется поле nRate в записи сущности HistoryEvent_Service, которая находится по sID_Order или nID_Protected с/без nID_Server (подробнее тут, при этом приходящее значение из параметра sID_Rate должно содержать число от 1 до 5. т.е. возможные ошибки:\n\n"
	        + "- nID_Protected некорректное -- ошибка 403. CRC Error, пишется в лог (т.е. сообщение все равно сохраняется)\n"
	        + "- sID_Rate некорректное (не число или не в промежутке от 1 до 5) -- ошибка 403. Incorrect sID_Rate, пишется в лог\n"
	        + "- запись заявки (по nID_Protected без последней цифры) не найдена -- ошибка 403. Record not found, пишется в лог проверить запись HistoryEvent_Service можно через сервис"
	        + " \\sevices\\getHistoryEvent_Service?nID_Protected=xxx (link: 17. Работа с обьектами событий по услугам)\n\n"
	        + "Примеры:\n"
	        + "https://test.igov.org.ua/wf/service/messages/setMessage?sHead=name&sBody=body&sMail=a@a.a\n"
	        + "Ответ: Status 200 если Ok\n" )
    @RequestMapping(value = "/setMessage", method = RequestMethod.POST)
    public @ResponseBody
    ResponseEntity setMessage(
	    @ApiParam(value = "Строка-заглавие сообщения", required = true) @RequestParam(value = "sHead") String sHead,
	    @ApiParam(value = "Строка-тело сообщения", required = false) @RequestParam(value = "sBody", required = false) String sBody,
	    @ApiParam(value = "ИД-номер субьекта (автора) (добавляется в запрос автоматически после аутентификации пользователя)", required = false) @RequestParam(value = "nID_Subject", required = false) Long nID_Subject,
	    @ApiParam(value = "Строка электронного адреса автора", required = false) @RequestParam(value = "sMail", required = false) String sMail,
	    @ApiParam(value = "Строка контактов автора", required = false) @RequestParam(value = "sContacts", required = false) String sContacts,
	    @ApiParam(value = "Строка дополнительных данных автора", required = false) @RequestParam(value = "sData", required = false) String sData,
	    @ApiParam(value = "ИД-номер типа сообщения", required = false) @RequestParam(value = "nID_SubjectMessageType", required = false) Long nID_SubjectMessageType //,
            ) throws ActivitiRestException {

        SubjectMessage message
                = createSubjectMessage(sHead, sBody, nID_Subject, sMail, sContacts, sData, nID_SubjectMessageType);
        subjectMessagesDao.setMessage(message);
        message = subjectMessagesDao.getMessage(message.getId());
        return JsonRestUtils.toJsonResponse(message);
    }

    /**
     * Сохранение сообщения по услуге
     * @param sID_Order Строка-ИД заявки
     * @param sBody Строка-тело сообщения
     * @param nID_SubjectMessageType ИД-номер типа сообщения  //опционально (по умолчанию == 0)
     */
    @ApiOperation(value = "Сохранение сообщения по услуге", notes = "" )
    @RequestMapping(value = "/setServiceMessage", method = RequestMethod.POST)
    public @ResponseBody
    ResponseEntity setServiceMessage(
	    @ApiParam(value = "Строка-ИД заявки", required = true) @RequestParam(value = "sID_Order", required = true) String sID_Order,
	    @ApiParam(value = "Строка-тело сообщения", required = true) @RequestParam(value = "sBody", required = true) String sBody,
	    @ApiParam(value = "ИД-номер типа сообщения", required = true) @RequestParam(value = "nID_SubjectMessageType", required = true) Long nID_SubjectMessageType //,//, defaultValue = "4"
            ) throws ActivitiRestException {

        Long nID_HistoryEvent_Service;
        Long nID_Subject;
        SubjectMessage oSubjectMessage;
        try {
            HistoryEvent_Service oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
            nID_HistoryEvent_Service = oHistoryEvent_Service.getId();
            nID_Subject = oHistoryEvent_Service.getnID_Subject();
            historyEventServiceDao.saveOrUpdate(oHistoryEvent_Service);
            oSubjectMessage
                    = createSubjectMessage(sMessageHead(nID_SubjectMessageType, sID_Order),sBody, nID_Subject, "", "", "", nID_SubjectMessageType);
            oSubjectMessage.setnID_HistoryEvent_Service(nID_HistoryEvent_Service);
            subjectMessagesDao.setMessage(oSubjectMessage);
            
        } catch (Exception e) {
            LOG.error("FAIL:", e);
                throw new ActivitiRestException(500, "[setServiceMessage]{sID_Order="+sID_Order+"}:"+e.getMessage());
        }
        return JsonRestUtils.toJsonResponse(oSubjectMessage);
    }
    
    public String sMessageHead(Long nID_SubjectMessageType, String sID_Order){
        String sHead = "";
        if (nID_SubjectMessageType == -1l){
            sHead = "";
        } else if (nID_SubjectMessageType == 1l) {
            sHead = "Оцінка по відпрацьованій послузі за заявою " + sID_Order;
        } else if (nID_SubjectMessageType == 2l) {
            sHead = "Відгук по відпрацьованій послузі за заявою " + sID_Order;
        } else if (nID_SubjectMessageType == 4l) {
            sHead = "Введений коментар клієнта по заяві " + sID_Order;
        } else if (nID_SubjectMessageType == 5l) {
            sHead = "Введений коментар співробітника по заяві " + sID_Order;
        } else if (nID_SubjectMessageType == 6l) {
            sHead = "Уточнююча оцінка по відпрацьованій послузі за заявою " + sID_Order;
        } else if (nID_SubjectMessageType == 7l) {
            sHead = "Уточнюючий коментар клієнта по заяві " + sID_Order;
        }
        return sHead;
    }
    
    
    /**
     * Сохранение сообщения оценки
     * @param sID_Order Строка-ИД заявки (временно опциональный)
     * @param sID_Rate Строка-ИД Рнйтинга/оценки (число от 1 до 5)
     * @param nID_Protected Номер-ИД заявки, защищенный по алгоритму Луна, опционально(для обратной совместимости)
     * @throws ActivitiRestException
     */
    @ApiOperation(value = "/setMessageRate", notes = "##### SubjectMessageController - Сообщения субьектов. Установка сообщения-оценки #####\n\n" )
    @RequestMapping(value = "/setMessageRate", method = RequestMethod.GET)//Rate
    public @ResponseBody
    String setMessageRate(
	    @ApiParam(value = "Строка-ИД заявки (временно опциональный)", required = false) @RequestParam(value = "sID_Order", required = false) String sID_Order,
	    @ApiParam(value = "Строка-ИД рейтинга/оценки (число от 1 до 5)", required = true) @RequestParam(value = "sID_Rate", required = true) String sID_Rate,
 	     @ApiParam(value = "Номер-ИД заявки, защищенный по алгоритму Луна, опционально(для обратной совместимости)", required = false) @RequestParam(value = "nID_Protected", required = false) Long nID_Protected,
            HttpServletResponse oResponse) throws ActivitiRestException {

        if(sID_Order==null){
            if(nID_Protected==null){
                LOG.error("sID_Order=null and nID_Protected=null");
            }else{
                LOG.warn("sID_Order=null and nID_Protected="+nID_Protected);
                sID_Order = "0-"+nID_Protected;
            }
        }
        if (!sID_Order.contains("-")) {
            LOG.warn("Incorrect parameter! {sID_Order}", sID_Order);
            throw new ActivitiRestException(404, "Incorrect parameter! {sID_Order=" + sID_Order + "}");
        }
        
        
        if ("".equals(sID_Rate.trim())) {
            LOG.warn("Parameter(s) is absent! {sID_Order}, {sID_Rate}", sID_Order, sID_Rate);
            throw new ActivitiRestException(404, "Incorrect value of sID_Rate! It isn't number.");
        }
        Integer nRate;
        try {
            nRate = Integer.valueOf(sID_Rate);
        } catch (NumberFormatException ex) {
            LOG.warn("incorrect param sID_Rate (not a number): " + sID_Rate);
            throw new ActivitiRestException(404, "Incorrect value of sID_Rate! It isn't number.");
        }
        if (nRate < 1 || nRate > 5) {
            LOG.warn("incorrect param sID_Rate (not in range[1..5]): " + sID_Rate);
            throw new ActivitiRestException(404, "Incorrect value of sID_Rate! It is too short or too long number");
        }        
        
        
        String sReturn = "Ok!";

        Long nID_HistoryEvent_Service;
        Long nID_Subject;
        HistoryEvent_Service oHistoryEvent_Service;
        
        try {
            //LOG.info("sID_Order: " + sID_Order + ", nRate: " + nRate);
            oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
            if(oHistoryEvent_Service==null){
                throw new ActivitiRestException(404, "(sID_Order: " + sID_Order + ", nRate: " + nRate + "): Record of HistoryEvent_Service, with sID_Order="+sID_Order+" - not found!");
            }
            nID_HistoryEvent_Service = oHistoryEvent_Service.getId();
            nID_Subject = oHistoryEvent_Service.getnID_Subject();

            String sToken = null;
            Integer nRateWas = oHistoryEvent_Service.getnRate();
            if(nRateWas!=null && nRateWas > 0){
                //throw new ActivitiRestException(404, "(sID_Order: " + sID_Order + "): Record of HistoryEvent_Service, with sID_Order="+sID_Order+" - alredy has nRateWas="+nRateWas);
                sReturn = "Record of HistoryEvent_Service, with sID_Order=" + sID_Order + " - already has nRateWas=" + nRateWas;
                LOG.warn("{} (nID_HistoryEvent_Service={}, nID_Subject={})", sReturn, nID_HistoryEvent_Service, nID_Subject);
            }else{
            
                oHistoryEvent_Service.setnRate(nRate);
                //LOG.info(String.format("set nRate=%s in sID_Order=%s", nRate, sID_Order));
                sToken = RandomStringUtils.randomAlphanumeric(15);
                //HistoryEvent_Service oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
                oHistoryEvent_Service.setsToken(sToken);
                
                LOG.info("save HistoryEvent_Service... (nID_HistoryEvent_Service={}, nID_Subject={}, sID_Order={}, nRate={})"
                        ,nID_HistoryEvent_Service, nID_Subject, sID_Order, nRate);
                historyEventServiceDao.saveOrUpdate(oHistoryEvent_Service);
                
                Long nID_SubjectMessageType = 0l;
                SubjectMessage oSubjectMessage_Rate
                        = createSubjectMessage(
                                sMessageHead(nID_SubjectMessageType, sID_Order),
                                "Оцінка " + sID_Rate + " (по шкалі від 2 до 5)"
                                , nID_Subject, "", "", "sID_Rate=" + sID_Rate, nID_SubjectMessageType);
                if(nID_HistoryEvent_Service!=null){
                    oSubjectMessage_Rate.setnID_HistoryEvent_Service(nID_HistoryEvent_Service);
                }
                subjectMessagesDao.setMessage(oSubjectMessage_Rate);
            }
            
            //сохранения сообщения с рейтингом, а на ррегиональном сервере, т.к. именно там хранится экземпляр БП.
            if (oHistoryEvent_Service.getnID_Proccess_Feedback() != null) {//issue 1006
                String snID_Process = "" + oHistoryEvent_Service.getnID_Proccess_Feedback();
                Integer nID_Server = oHistoryEvent_Service.getnID_Server();
                LOG.info("set rate={} to the nID_Proccess_Feedback={}", nRate, snID_Process);
                List<String> aTaskIds = bpService.getProcessTasks(nID_Server, snID_Process);
                LOG.info("Found '{}' tasks by nID_Proccess_Feedback...",aTaskIds.size());
                if (aTaskIds.size() > 0) {//when process is not complete
                    bpService.setVariableToProcessInstance(nID_Server, snID_Process, "nID_Rate", nRate);
                    LOG.info("process is not complete -- change rate in it!");
                    for (String sTaskId : aTaskIds) {
                        bpService.setVariableToActivitiTask(nID_Server, sTaskId, "nID_Rate", nRate);
                    }
                }
            }
            String sURL_Redirect = generalConfig.sHostCentral() + "/feedback?sID_Order=" + sID_Order + "&sSecret=" + sToken;
            LOG.info("Redirecting to URL:" + sURL_Redirect);
            oResponse.sendRedirect(sURL_Redirect);
            
        } catch (ActivitiRestException oActivitiRestException) {
            LOG.error("FAIL: {}", oActivitiRestException.getMessage());
            throw oActivitiRestException;
        } catch (Exception e) {
            LOG.error("FAIL:", e);
            throw new ActivitiRestException(404, "[setMessageRate](sID_Order: " + sID_Order + ", nRate: " + nRate + "): Unknown exception: "+e.getMessage());
        }        
        
        //subjectMessagesDao.setMessage(oSubjectMessage_Rate);
        
        /*String sToken = RandomStringUtils.randomAlphanumeric(15);
        try {
            HistoryEvent_Service oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
            nID_HistoryEvent_Service = oHistoryEvent_Service.getId();
            nID_Subject = oHistoryEvent_Service.getnID_Subject();
            oHistoryEvent_Service.setsToken(sToken);
            historyEventServiceDao.saveOrUpdate(oHistoryEvent_Service);
        } catch (Exception e) {
                LOG.error("Error occured while saving sID_Order in subject message for feedback.", e);;
        }*/
        
        /*Long nID_SubjectMessageType = 0l;
        SubjectMessage oSubjectMessage_Rate
                = createSubjectMessage(
                        sMessageHead(nID_SubjectMessageType, sID_Order),
                        "Оцінка " + sID_Rate + " (по шкалі від 2 до 5)"
                        , nID_Subject, "", "", "sID_Rate=" + sID_Rate, nID_SubjectMessageType);
        if(nID_HistoryEvent_Service!=null){
            oSubjectMessage_Rate.setnID_HistoryEvent_Service(nID_HistoryEvent_Service);
        }
        subjectMessagesDao.setMessage(oSubjectMessage_Rate);*/
        //setServiceRate(sID_Order, sID_Rate);
        
        // storing message for feedback
        /*try {
            String sURL_Redirect = generalConfig.sHostCentral() + "/feedback?sID_Order=" + sID_Order + "&sSecret=" + sToken;
            LOG.info("Redirecting to URL:" + sURL_Redirect);
            oResponse.sendRedirect(sURL_Redirect);
        } catch (Exception e) {
            LOG.error("Error occured while saving subject message for feedback.", e);;
        }*/
        return sReturn;//"Ok!";
    }

    /**
     * получение массива сообщений
     */
    @ApiOperation(value = "Получение массива сообщений ", notes = "##### SubjectMessageController - Сообщения субьектов. Получение массива сообщений #####\n\n"
	        + "HTTP Context: http://server:port/wf/service/messages/getMessages\n\n\n"
	        + "Примеры:\n"
	        + "https://test.igov.org.ua/wf/service/messages/getMessages\n\n"
	        + "nID_Subject - ID авторизированого субъекта (добавляется в запрос автоматически после аутентификации пользователя)\n"
	        + "Response:\n"
	        + "\n```json\n"
	        + "[\n"
	        + "    {\n"
	        + "        \"nID\":76,\"sHead\":\"Закликаю владу перевести цю послугу в електронну форму!\"\n"
	        + "        ,\"sBody\":\"Дніпропетровськ - Видача витягу з технічної документації про нормативну грошову оцінку земельної ділянки\"\n"
	        + "        ,\"sDate\":\"2015-06-03 22:09:16.536\"\n"
	        + "        ,\"nID_Subject\":0\n"
	        + "        ,\"sMail\":\"bvv4ik@gmail.com\"\n"
	        + "        ,\"sContacts\":\"\"\n"
	        + "        ,\"sData\":\"\"\n"
	        + "        ,\"oSubjectMessageType\": {\n"
	        + "            \"sDescription\": \"Просьба добавить услугу\",\n"
	        + "            \"nID\": 0,\n"
	        + "            \"sName\": \"ServiceNeed\"\n"
	        + "        }\n"
	        + "    }\n"
	        + "]\n"
	        + "\n```\n" )
    @RequestMapping(value = "/getMessages", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, headers = {"Accept=application/json"})
    public @ResponseBody
    ResponseEntity getMessages() {

        List<SubjectMessage> messages = subjectMessagesDao.getMessages();
        return JsonRestUtils.toJsonResponse(messages);
    }
    
    /**
     * получение массива сообщений по услуге
     * @param sID_Order Строка-ИД заявки
     * @return array of messages by sID_Order
     */
    @ApiOperation(value = "Получение массива сообщений по услуге", notes = "##### SubjectMessageController - Сообщения субьектов. Получение массива сообщений по услуге #####\n\n" )
    @RequestMapping(value = "/getServiceMessages", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, headers = {"Accept=application/json"})
    public @ResponseBody
    ResponseEntity getServiceMessages(
	    @ApiParam(value = "Строка-ИД заявки", required = true) @RequestParam(value = "sID_Order", required = true) String sID_Order
        ) throws ActivitiRestException {
        Long nID_HistoryEvent_Service;
        Long nID_Subject = null;
        //SubjectMessage oSubjectMessage = null;
        List<SubjectMessage> aSubjectMessage;
        try {
            HistoryEvent_Service oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
            nID_HistoryEvent_Service = oHistoryEvent_Service.getId();
            nID_Subject = oHistoryEvent_Service.getnID_Subject();
            historyEventServiceDao.saveOrUpdate(oHistoryEvent_Service);
            
            /*String sHead = "";
            if (nID_SubjectMessageType == 4l){
                sHead = "Введений коментар клієнта по заяві " + sID_Order;
            }*/

            /*oSubjectMessage
                    = createSubjectMessage(sHead,sBody, nID_Subject, "", "", "", nID_SubjectMessageType);
            oSubjectMessage.setnID_HistoryEvent_Service(nID_HistoryEvent_Service);
            subjectMessagesDao.setMessage(oSubjectMessage);*/

            aSubjectMessage = subjectMessagesDao.getMessages(nID_HistoryEvent_Service);
            
        } catch (Exception e) {
            LOG.error("FAIL:", e);
                throw new ActivitiRestException(500, "[setServiceMessage]{sID_Order="+sID_Order+"}:"+e.getMessage());
        }
        return JsonRestUtils.toJsonResponse(aSubjectMessage);
    }
     
    

    /**
     * получение сообщения
     * @param nID ID сообщения
     */
    @ApiOperation(value = "Получение сообщения", notes = "##### SubjectMessageController - Сообщения субьектов. Получение сообщения #####\n\n"
	        + "HTTP Context: http://server:port/wf/service/messages/getMessage\n\n\n"
	        + "Примеры: https://test.igov.org.ua/wf/service/messages/getMessage?nID=76\n\n"
	        + "\n```json\n"
	        + "Ответ:\n"
	        + "{\n"
	        + "    \"nID\":76\n"
	        + "    ,\"sHead\":\"Закликаю владу перевести цю послугу в електронну форму!\"\n"
	        + "    ,\"sBody\":\"Дніпропетровськ - Видача витягу з технічної документації про нормативну грошову оцінку земельної ділянки\"\n"
	        + "    ,\"sDate\":\"2015-06-03 22:09:16.536\"\n"
	        + "    ,\"nID_Subject\":0\n"
	        + "    ,\"sMail\":\"bvv4ik@gmail.com\"\n"
	        + "    ,\"sContacts\":\"\"\n"
	        + "    ,\"sData\":\"\"\n"
	        + "    ,\"oSubjectMessageType\": {\n"
	        + "        \"sDescription\": \"Просьба добавить услугу\",\n"
	        + "        \"nID\": 0,\n"
	        + "        \"sName\": \"ServiceNeed\"\n"
	        + "    }\n"
	        + "}\n"
	        + "\n```\n" )
    @RequestMapping(value = "/getMessage", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE, headers = {"Accept=application/json"})
    public @ResponseBody
    ResponseEntity getMessage(
	    @ApiParam(value = "", required = true) @RequestParam(value = "nID") Long nID) {

        SubjectMessage message = subjectMessagesDao.getMessage(nID);
        return JsonRestUtils.toJsonResponse(message);
    }

    @ApiOperation(value = "/setMessageFeedback_Indirectly", notes = "##### SubjectMessageController - Сообщения субьектов. нет описания #####\n\n" )
    @RequestMapping(value = "/setMessageFeedback_Indirectly", method = RequestMethod.GET)
    public @ResponseBody
    String setMessageFeedback_Indirectly(
            @ApiParam(value = "Номер-ИД заявки, защищенный по алгоритму Луна", required = true)
            @RequestParam(value = "nID_Protected", required = true) Long nID_Protected,
            @ApiParam(value = "Номер-ИД бп фидбека по заявке", required = true)
            @RequestParam(value = "nID_Proccess_Feedback", required = true) String nID_Proccess_Feedback,
            @ApiParam(value = "Строка отзыв об услуге", required = true)
            @RequestParam(value = "sBody_Indirectly", required = true) String sBody_Indirectly,
            @ApiParam(value = "Строка-ИД Рейтинга/оценки (число от 1 до 5)", required = true)
            @RequestParam(value = "sID_Rate_Indirectly", required = true) String sID_Rate_Indirectly,
            @ApiParam(value = "ИД сервера, где размещена заявка (опционально, по умолчанию 0)", required = false)
            @RequestParam(value = "nID_Server", required = false, defaultValue = "0") Integer nID_Server)
            throws ActivitiRestException {

        Optional<HistoryEvent_Service> eventServiceOptional = historyEventServiceDao.findBy("nID_Proccess_Feedback", Long.valueOf(nID_Proccess_Feedback));
        if (eventServiceOptional.isPresent()) {
            HistoryEvent_Service historyEventService = eventServiceOptional.get();
            if (historyEventService != null) {
                historyEventService.setsID_Rate_Indirectly(sID_Rate_Indirectly);
                historyEventServiceDao.saveOrUpdate(historyEventService);
                LOG.info("Successfully updated historyEvent_Service with the rate " + sID_Rate_Indirectly);
                /////issue 1037
                // create rate-message
                String sID_Order = "" + (nID_Server != null ? nID_Server : 0) + "-" + nID_Protected;
                SubjectMessage oSubjectMessage_Rate = createSubjectMessage(
                        sMessageHead(6L, sID_Order),
                        "Оцінка " + sID_Rate_Indirectly + " (по шкалі від 2 до 5)"
                        , historyEventService.getnID_Subject(), "", "", "sID_Rate=" + sID_Rate_Indirectly, 6L);
                oSubjectMessage_Rate.setnID_HistoryEvent_Service(historyEventService.getId());
                subjectMessagesDao.setMessage(oSubjectMessage_Rate);
                LOG.info("Successfully created SubjectMessage:" + oSubjectMessage_Rate.getHead());
                ///// create note-message
                oSubjectMessage_Rate = createSubjectMessage(
                        sMessageHead(7L, sID_Order), sBody_Indirectly,
                        historyEventService.getnID_Subject(), "", "", "sID_Rate=" + sID_Rate_Indirectly, 7L);
                oSubjectMessage_Rate.setnID_HistoryEvent_Service(historyEventService.getId());
                subjectMessagesDao.setMessage(oSubjectMessage_Rate);
                LOG.info("Successfully created SubjectMessage:" + oSubjectMessage_Rate.getHead());
                /////
            }
        } else {
            LOG.error("Didn't find event service");
            return "Ok";
        }
        LOG.error("Finished execution");
        return "Ok";
    }

    private SubjectMessage createSubjectMessage(String sHead, String sBody, Long nID_subject, String sMail,
            String sContacts, String sData, Long nID_subjectMessageType) {
          SubjectContact subjectContact = new SubjectContact();
        Subject subject = new Subject();
        
        if(sMail != null && !sMail.equals(""))
        {
          if(nID_subject != null)
              syncMail(sMail, nID_subject, subjectContact);
          if(nID_subject == null)
              syncMail(sMail, subject, subjectContact);
        }
     
        SubjectMessage message = new SubjectMessage();
        message.setHead(sHead);
        message.setBody(sBody == null ? "" : sBody);
        message.setId_subject((nID_subject == null) ? ((subject.getId() == null) ? 0 :subject.getId()) : nID_subject);
        message.setoMail((subjectContact.getId() == null) ? null : subjectContact);
        //message.setMail((sMail == null) ? "" : sMail);
        message.setContacts((sContacts == null) ? "" : sContacts);
        message.setData((sData == null) ? "" : sData);
        message.setDate(new DateTime());
        if (nID_subjectMessageType != null) {
            SubjectMessageType subjectMessageType = subjectMessageTypeDao.findByIdExpected(nID_subjectMessageType);
            message.setSubjectMessageType(subjectMessageType);
        }
       return message;
    }
     //при параметре nID_Subject == null
     private void syncMail(String sMail, Subject oSubject, SubjectContact subjectContact) {
         SubjectHuman oSubjectHuman = subjectHumanDao.getSubjectHuman(SubjectHumanIdType.Email, sMail);
           
             Subject subject = (oSubjectHuman != null) ? oSubjectHuman.getoSubject() : null;
            if(subject != null)
            {
             oSubject.setId(subject.getId());
             oSubject.setsID(subject.getsID());
             oSubject.setsLabel(subject.getsLabel());
             oSubject.setsLabelShort(subject.getsLabelShort());
           
             SubjectContact mailContact = subjectContactDao.findByExpected("sValue", sMail);
            if(mailContact != null)
            {
             subjectContact.setId(mailContact.getId());
             subjectContact.setSubject(subject);
             subjectContact.setSubjectContactType(mailContact.getSubjectContactType());
             subjectContact.setsDate();
             subjectContact.setsValue(mailContact.getsValue());
              
             subjectContactDao.saveOrUpdate(subjectContact);
            }
            
             
            }
            
        
     }
     //при параметре nID_Subject != null
     private void syncMail(String sMail, Long nID_Subject, SubjectContact subjectContact)
    {
    
             
               Subject subject = subjectDao.getSubject(nID_Subject);
               SubjectHuman subjectHuman = subjectHumanDao.findByExpected("oSubject", subject);
             
               List<SubjectContact> subjectContacts = subjectContactDao.findContacts(subject);

               boolean subjcontact = true;
               
                    for(int j = 0; j < subjectContacts.size(); j++)
                    {
                       SubjectContactType sct = subjectContacts.get(j).getSubjectContactType();
                       if(sct.getsName_EN().equals("Email"))
                       {
                          if(subjectContacts.get(j).getsValue().equals(sMail))
                          {
                              subjcontact = false;
                              subjectContact.setId(subjectContacts.get(j).getId());
                              subjectContact.setSubject(subject);
                              subjectContact.setSubjectContactType(subjectContacts.get(j).getSubjectContactType());
                              subjectContact.setsDate();
                              subjectContact.setsValue(subjectContacts.get(j).getsValue());
                          }
                         
                       }
                    }
                    
                    if(subjcontact)
                    {
                        SubjectContactType subjectContactType = subjectContactTypeDao.getEmailType();
                       subjectContact.setSubject(subject);
                       subjectContact.setSubjectContactType(subjectContactType);
                       subjectContact.setsValue(sMail);
                       subjectContact.setsDate();
                       subjectContactDao.saveOrUpdate(subjectContact);
                       subjectHuman.setDefaultEmail(subjectContact);
                       subjectHumanDao.saveOrUpdateHuman(subjectHuman); 
                    }
              
          
    }
     
   
    //private void checkRate(String sID_Order, Long nID_Protected, Integer nID_Server, String sID_Rate)
    /*private void setServiceRate(String sID_Order, String sID_Rate)
            throws ActivitiRestException {

        //if (nID_Protected == null && sID_Order == null && nID_Server == null && sID_Rate == null) {
        if (sID_Order == null || sID_Rate == null) {
            LOG.warn("Parameter(s) is absent! {sID_Order}, {sID_Rate}", sID_Order, sID_Rate);
            throw new ActivitiRestException(404, "Incorrect value of sID_Rate! It isn't number.");
            //return;
        }
        if (!sID_Order.contains("-")) {
            LOG.warn("Incorrect parameter! {sID_Order}", sID_Order);
            throw new ActivitiRestException(404, "Incorrect parameter! {sID_Order=" + sID_Order + "}");
        }
        
        if (sID_Rate != null && !sID_Rate.trim().equals("")) {
            Integer nRate;
            try {
                nRate = Integer.valueOf(sID_Rate);
            } catch (NumberFormatException ex) {
                LOG.warn("incorrect param sID_Rate (not a number): " + sID_Rate);
                throw new ActivitiRestException(404, "Incorrect value of sID_Rate! It isn't number.");
            }
            if (nRate < 1 || nRate > 5) {
                LOG.warn("incorrect param sID_Rate (not in range[1..5]): " + sID_Rate);
                throw new ActivitiRestException(404, "Incorrect value of sID_Rate! It is too short or too long number");
            }
            try {
                HistoryEvent_Service oHistoryEvent_Service;
                LOG.info("sID_Order: " + sID_Order + ", nRate: " + nRate);
                oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
                oHistoryEvent_Service.setnRate(nRate);
                LOG.info(String.format("set rate=%s to the task=%s, nID_Protected=%s", nRate,
                oHistoryEvent_Service.getnID_Task(), oHistoryEvent_Service.getnID_Protected()));
                historyEventServiceDao.saveOrUpdate(oHistoryEvent_Service);
                if (oHistoryEvent_Service.getnID_Proccess_Feedback() != null) {//issue 1006
                    String snID_Process = "" + oHistoryEvent_Service.getnID_Proccess_Feedback();
                    LOG.info(String.format("set rate=%s to the nID_Proccess_Feedback=%s", nRate, snID_Process));
                    List<Task> aTask = taskService.createTaskQuery().processInstanceId(snID_Process).list();
                    if (aTask.size() > 0) {//when process is not complete
                        runtimeService.setVariable(snID_Process, "nID_Rate", nRate);
                        LOG.info("Found " + aTask.size() + " tasks by nID_Proccess_Feedback...");
                        for (Task oTask : aTask) {
                            LOG.info("oTask;getName=" + oTask.getName() + "|getDescription=" + oTask.getDescription() + "|getId=" + oTask.getId());
                            taskService.setVariable(oTask.getId(), "nID_Rate", nRate);
                        }
                    }
                }
                LOG.info(String.format("set rate=%s to the task=%s, nID_Protected=%s Success!",
                        nRate, oHistoryEvent_Service.getnID_Task(), oHistoryEvent_Service.getnID_Protected()));
            } catch (CRCInvalidException e) {
                LOG.error(""+e.getMessage(), e);
            } catch (Exception e) {
                LOG.error("ex!", e);
            }
        }
    }*/
    
    @ApiOperation(value = "Получить сообщение-фидбек заявки", notes = "##### SubjectMessageController - Сообщения субьектов. Получить сообщения-фидбека заявки #####\n\n"
		+ "HTTP Context: https://test.igov.org.ua/wf/service/messages/getMessageFeedbackExtended?sID_Order=XXX-XXXXXX&sToken=[TokenValue]*\n\n"
		+ "получает сообщение-фидбека:\n\n\n"
		+ "Если объект не найден по sID_Order, то возвращается код 404 и сообщение \"Record Not Found\"\n"
		+ "Если sToken<>'' и sToken<>null и sToken не совпадет с HistoryEvent_Service.sToken то возвращается 403 статус и сообщение \"Security Error\"\n"
		+ "если в найденном обекте SubjectMessage sBody='', то sDate в результате возвращается как null\n"
		+ "Пример:\n"
		+ "https://test.igov.org.ua/wf/service/messages/getMessageFeedbackExtended?sID_Order=0-4446&sToken=TokenValue"
		+ "\n```json\n"
		+ "{\n"
		+ "    \"sDate\":\"2015-11-10 23:23:59 001\",\n"
		+ "    \"sHead\":\"Получение справки о доходах\",\n"
		+ "    \"sID_Order\":\"0-4446\",\n"
		+ "}\n"
		+ "\n```\n" )
    @ApiResponses(value = { @ApiResponse(code = 403, message = "Security Error (если не совпадает токен)"),
	    @ApiResponse(code = 404, message = "Record not found") } )
    @RequestMapping(value = "/getMessageFeedbackExtended", method = RequestMethod.GET)//Feedback
    /**
     * Получение сообщение-фидбека заявки по следующим параметрам:
     * @param sID_Order  строка-ид события по услуге, формат XXX-XXXXXX, где первая часть -- ид сервера, где расположена задача, вторая часть -- nID_Protected, т.е. ид задачи + контрольная сумма по алгоритму Луна
     * @param sToken токен, который сранивается со значением sToken из объекта HistoryEvent_Service
     * @return json со значениями sDate, sHead, sID_Order
     * @throws ActivitiRestException 
     * 	404 ошибка и сообщение "Record Not Found" - если запись не найдена
     * 	403 ошибка и сообщение "Security Error" - если не совпадает токен
     */
    // (формат XXX-XXXXXX, где первая часть -- ид сервера, где расположена задача, вторая часть -- nID_Protected, т.е. ид задачи + контрольная сумма по алгоритму Луна)
    public @ResponseBody
    Map<String, Object> getMessageFeedbackExtended(
	    @ApiParam(value = "Строка-ИД заявки услуги", required = true) @RequestParam(value = "sID_Order") String sID_Order,
	    @ApiParam(value = "Строка-токен (защита от постороннего доступа)", required = true) @RequestParam(value = "sToken") String sToken,
	    @ApiParam(value = "Номер-ИД типа сообщения", required = false) @RequestParam(value = "nID_SubjectMessageType", defaultValue = "2") Long nID_SubjectMessageType
            ) throws ActivitiRestException {

        Map<String, Object> mReturn = new HashMap<>();

		try {
                    if ("".equals(sToken.trim())){
                        LOG.warn("Wrong sToken: " + sToken);
                        throw new ActivitiRestException(
                        ExceptionCommonController.BUSINESS_ERROR_CODE,
                        "Security Error",
                        HttpStatus.FORBIDDEN);
                    }
                    HistoryEvent_Service oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
                    if (oHistoryEvent_Service != null){
	    		//if (oHistoryEvent_Service.getsToken() != null && oHistoryEvent_Service.getsToken().equals(sToken)){
		    		List<SubjectMessage> aSubjectMessage = subjectMessagesDao.findAllBy("nID_HistoryEvent_Service", oHistoryEvent_Service.getId());
                                SubjectMessage oSubjectMessage_Found = null;
		    		if (aSubjectMessage != null && aSubjectMessage.size() > 0){
		    			for (SubjectMessage oSubjectMessage : aSubjectMessage){
		    				if (Objects.equals(oSubjectMessage.getSubjectMessageType().getId(), nID_SubjectMessageType)){//2
                                                        oSubjectMessage_Found=oSubjectMessage;
		    				} else {
		    					LOG.info("Skipping subject message from processing as its ID is: " + oSubjectMessage.getSubjectMessageType().getId());
		    				}
		    			}
		    		} else {
		    			LOG.info("No SubjectMessage objects found with nID_HistoryEvent_Service:" + oHistoryEvent_Service.getId());
		    		}
                                mReturn.put("sID_Order", sID_Order);
                                if(oSubjectMessage_Found!=null){
                                    mReturn.put("sHead", oSubjectMessage_Found.getHead());
                                    if (oSubjectMessage_Found.getBody() != null && !"".equals(oSubjectMessage_Found.getBody().trim())){
                                            SimpleDateFormat oSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS"); 
                                            mReturn.put("sDate", oSimpleDateFormat.format(oSubjectMessage_Found.getDate().toDate()));
                                    } else {
                                            mReturn.put("sDate", null);
                                    }
                                }else{
                                    mReturn.put("sHead", sMessageHead(nID_SubjectMessageType, sID_Order));
                                    mReturn.put("sDate", null);
                                }
                                return mReturn;
                                
	    		/*} else {
	    			LOG.info("Skipping history event service " + oHistoryEvent_Service.getId() + " from processing as it contains wrong token: " + 
	    						oHistoryEvent_Service.getsToken() + ":" + oHistoryEvent_Service.getsID_Order());
	    			throw new ActivitiRestException(
	                        ExceptionCommonController.BUSINESS_ERROR_CODE,
	                        "Security Error",
	                        HttpStatus.FORBIDDEN);
	    		}*/
                    }else{
                        LOG.warn("Skipping history event service, wrong sID_Order: " + sID_Order);
                        throw new ActivitiRestException(
                        ExceptionCommonController.BUSINESS_ERROR_CODE,
                        "Security Error",
                        HttpStatus.FORBIDDEN);
                    }
		} catch (CRCInvalidException e) {
			LOG.error("Error occurred while getting message feedback:" + e.getMessage());
		}
        
		throw new ActivitiRestException(
                ExceptionCommonController.BUSINESS_ERROR_CODE,
                "Record Not Found",
                HttpStatus.NOT_FOUND);
    }
    
    @ApiOperation(value = "Сохранить сообщение-фидбек заявки", notes = "##### SubjectMessageController - Сообщения субьектов. Сохранить сообщения-фидбека заявки #####\n\n"
		+ "HTTP Context: https://test.igov.org.ua/wf/service/messages/setMessageFeedbackExtended?sID_Order=XXX-XXXXXX&sToken=[TokenValue]*\n\n"
		+ "сохраняет сообщение-фидбек\n\n\n"
		+ "Если запись успешно добавлена/обновлена то устанавливается sToken='' и sDate устанавливается в текущую.\n"
		+ "Если запись найдена и sBody<>'', то возвращается статус 403 и сообщение \"Already exists\"\n"
		+ "Если запись не найдена и sBody<>'', то возвращается 404 статус и сообщение \"Record Not Found\"\n"
		+ "Если sToken<>'' и sToken<>null и sToken не совпадет с HistoryEvent_Service.sToken то возвращается 403 статус и сообщение \"Security Error\"" )
    @ApiResponses(value = { @ApiResponse(code = 403, message = "Already exist (если sBody в SubjectMessage не пустое ) / Security Error (если не совпадает токен)"),
	    @ApiResponse(code = 404, message = "Record not found") } )
    @RequestMapping(value = "/setMessageFeedbackExtended", method = RequestMethod.POST)//Feedback
    /**
     * Сохранение сообщение-фидбека заявки
     * @param sID_Order  строка-ид события по услуге, формат XXX-XXXXXX, где первая часть -- ид сервера, где расположена задача, вторая часть -- nID_Protected, т.е. ид задачи + контрольная сумма по алгоритму Луна
     * @param sToken токен, который сранивается со значением sToken из объекта HistoryEvent_Service
     * @param sBody строка текста фидбэка
     * 
     * @throws ActivitiRestException 
     * 	404 ошибка и сообщение "Record Not Found" - если запись не найдена
     * 	403 ошибка и сообщение "Security Error" - если не совпадает токен
     *  403 ошибка и сообщение "Already exist" - если sBody в SubjectMessage не пустое 
     */
    public @ResponseBody
    String setMessageFeedbackExtended(
	    @ApiParam(value = "Строка-ИД заявки услуги", required = true) @RequestParam(value = "sID_Order") String sID_Order,
	    @ApiParam(value = "Строка-токен (защита от постороннего доступа)", required = true) @RequestParam(value = "sToken") String sToken,
	    @ApiParam(value = "Номер-ИД типа сообщения", required = false) @RequestParam(value = "nID_SubjectMessageType", defaultValue = "2") Long nID_SubjectMessageType,
	    @ApiParam(value = "строка текста фидбэка", required = true) @RequestParam(value = "sBody") String sBody) throws ActivitiRestException {

		try {
                    if ("".equals(sToken.trim())){
                        LOG.warn("Wrong sToken: " + sToken);
                        throw new ActivitiRestException(
                        ExceptionCommonController.BUSINESS_ERROR_CODE,
                        "Security Error",
                        HttpStatus.FORBIDDEN);
                    }
                    if (2l!=nID_SubjectMessageType){
                        LOG.warn("Wrong nID_SubjectMessageType: " + nID_SubjectMessageType);
                        throw new ActivitiRestException(
                        ExceptionCommonController.BUSINESS_ERROR_CODE,
                        "Security Error",
                        HttpStatus.FORBIDDEN);
                    }
                    HistoryEvent_Service oHistoryEvent_Service = historyEventServiceDao.getOrgerByID(sID_Order);
                    if (oHistoryEvent_Service != null){
	    		if (oHistoryEvent_Service.getsToken() != null && oHistoryEvent_Service.getsToken().equals(sToken)){
		    		/*List<SubjectMessage> aSubjectMessage = subjectMessagesDao.findAllBy("nID_HistoryEvent_Service", oHistoryEvent_Service.getId());
		    		if (aSubjectMessage != null && aSubjectMessage.size() > 0){
		    			for (SubjectMessage oSubjectMessage : aSubjectMessage){
		    				if (oSubjectMessage.getBody() != null && !oSubjectMessage.getBody().trim().isEmpty()){
		    					LOG.warn("Body in Subject message does already exist");
		    					throw new ActivitiRestException(
		    	                        ExceptionCommonController.BUSINESS_ERROR_CODE,
		    	                        "Already exists",
		    	                        HttpStatus.FORBIDDEN);
		    				} else {
		    					Optional<SubjectMessageType> subjectMessageType = subjectMessageTypeDao.findById(nID_SubjectMessageType);
		    					
		    					oSubjectMessage.setDate(new DateTime());
		    					oSubjectMessage.setBody(sBody);
		    					if (subjectMessageType.isPresent()){
		    						oSubjectMessage.setSubjectMessageType(subjectMessageType.get());
		    						LOG.info("Set SubjectMessageType with ID = "+nID_SubjectMessageType);
		    					}
		    					subjectMessagesDao.saveOrUpdate(oSubjectMessage);
		    					oHistoryEvent_Service.setsToken("");
		    					historyEventServiceDao.saveOrUpdate(oHistoryEvent_Service);
		    				}
		    			}
		    		} else {*/
                                        SubjectMessage oSubjectMessage_Feedback
                                                        = createSubjectMessage(sMessageHead(nID_SubjectMessageType, sID_Order)
                                                                , ""
                                                                , oHistoryEvent_Service.getnID_Subject(), "", "", "", nID_SubjectMessageType);//2l
                                        oSubjectMessage_Feedback.setnID_HistoryEvent_Service(oHistoryEvent_Service.getId());//nID_HistoryEvent_Service
                                        subjectMessagesDao.setMessage(oSubjectMessage_Feedback);
		    			LOG.info("No SubjectMessage records found, create new!");
                                        oHistoryEvent_Service.setsToken("");
                                        historyEventServiceDao.saveOrUpdate(oHistoryEvent_Service);
    					/*throw new ActivitiRestException(
    	                        ExceptionCommonController.BUSINESS_ERROR_CODE,
    	                        "Record Not Found",
    	                        HttpStatus.NOT_FOUND);*/
		    		//}
	    		} else {
	    			LOG.warn("Skipping history event service from processing as it contains wrong token: " + oHistoryEvent_Service.getsToken());
	    			throw new ActivitiRestException(
	                        ExceptionCommonController.BUSINESS_ERROR_CODE,
	                        "Security Error",
	                        HttpStatus.FORBIDDEN);
	    		}
                    }else{
                        LOG.warn("Skipping history event service, wrong sID_Order: " + sID_Order);
                        throw new ActivitiRestException(
                        ExceptionCommonController.BUSINESS_ERROR_CODE,
                        "Security Error",
                        HttpStatus.FORBIDDEN);
                    }
		} catch (CRCInvalidException e) {
			LOG.error("Error occurred while setting message feedback:" + e.getMessage());
		}
        
		return "Ok";
    }
    
    
    @ApiOperation(value = "Перенос данных из поля sMail в поле nID_SubjectContact_Mail таблицы SubjectMessage", notes = "##### SubjectMessageController - Сообщения субьектов. Переносит данные с поля sMail в nID_SubjectContact_Mail таблицы SubjectMessage. #####\n\n"
            + "Возвращает список из 100 первых измененных записей таблицы.  Метод также подчищает данные из sMail, устанавливая занчение null\n\n"
            + "HTTP Context: https://test.igov.org.ua/wf/service/messages/transferDataFromMail\n\n\n"
            + "Пример ответа:\n"
            + "\n```json\n"
            + "[\n"
            + " {\n"
            + "  \"mail\":\"test@igov.org.ua\",\n"
            + "  \"oMail\":{\"subjectContactType\":{\"sName_EN\":\"Email\",\"sName_UA\":\"Електрона адреса\",\"sName_RU\":\"Электнонный адрес\",\"nID\":1},\"sValue\":\"test@igov.org.ua\",\"sDate\":null,\"nID\":1},\n"
            + "  \"sBody_Indirectly\":\"Body Inderectly\",\n"
            + "  \"nID_HistoryEvent_Service\":3,\n"
            + "  \"nID\":1,\n"
            + "  \"sHead\":\"head\",\n"
            + "  \"sBody\":\"body of subject message\",\n"
            + "  \"sDate\":\"2015-12-21 14:09:56.235\",\n"
            + "  \"nID_Subject\":1,\n"
            + "  \"sContacts\":\"contact\",\n"
            + "  \"sData\":\"data\",\n"
            + "  \"oSubjectMessageType\":{\"sDescription\":\"Оценка услуги\",\"nID\":1,\"sName\":\"ServiceRate\"}\n"
            + " },\n"
            + " {\n"
            + "  \"mail\":\"test@igov.org.ua\",\n"
            + "  \"oMail\":{\"subjectContactType\":{\"sName_EN\":\"Email\",\"sName_UA\":\"Електрона адреса\",\"sName_RU\":\"Электнонный адрес\",\"nID\":1},\"sValue\":\"test@igov.org.ua\",\"sDate\":null,\"nID\":1},\n"
            + "  \"sBody_Indirectly\":\"Body Inderectly\",\n"
            + "  \"nID_HistoryEvent_Service\":4,\n"
            + "  \"nID\":2,\n"
            + "  \"sHead\":\"head2\",\n"
            + "  \"sBody\":\"\",\n"
            + "  \"sDate\":\"2015-12-21 14:09:56.235\",\n"
            + "  \"nID_Subject\":1,\n"
            + "  \"sContacts\":\"contact\",\n"
            + "  \"sData\":\"data\",\n"
            + "  \"oSubjectMessageType\":{\"sDescription\":\"Оценка услуги\",\"nID\":1,\"sName\":\"ServiceRate\"}\n"
            + " }\n"
            + "]\n"
            + "\n```\n"
            + "\n\nДанные из поля sMail таблицы SubjectMessage переносятся в поле nID_SubjectMessage_Mail (объект oMail).\n"
            + "Значения в поле sMail устанавливаются в null\n"
            + "Если происходит исключение во время переноса данных, возвращается 403.\n")
    @ApiResponses(value = {@ApiResponse(code = 403, message = "В случае появления исключения обработки sql-запросов"), 
        @ApiResponse(code = 200, message = "В случае успеха возвращает список первых 100 записей измененных с сущностями SubjectMessage")})
    @RequestMapping(value = "/transferDataFromMail", method = RequestMethod.GET)
    public @ResponseBody
    List transferDataFromMail() throws ActivitiRestException
    {
        List subjectMessages;
      try
      {
          subjectMessages = subjectMessagesDao.tranferDataFromMailToSubjectMail();
      }
      catch(Exception e)
      {
           throw new ActivitiRestException(
              ExceptionCommonController.BUSINESS_ERROR_CODE,
              e.getMessage(),
              HttpStatus.FORBIDDEN
           );
      }
       return subjectMessages;
    }

}
