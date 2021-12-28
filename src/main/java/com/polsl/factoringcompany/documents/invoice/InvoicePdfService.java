package com.polsl.factoringcompany.documents.invoice;

import com.polsl.factoringcompany.company.CompanyService;
import com.polsl.factoringcompany.customer.CustomerEntity;
import com.polsl.factoringcompany.customer.CustomerService;
import com.polsl.factoringcompany.invoice.InvoiceEntity;
import com.polsl.factoringcompany.invoice.InvoiceService;
import com.polsl.factoringcompany.invoiceitem.InvoiceItemEntity;
import com.polsl.factoringcompany.invoiceitem.InvoiceItemService;
import com.polsl.factoringcompany.product.ProductEntity;
import com.polsl.factoringcompany.product.ProductService;
import com.polsl.factoringcompany.user.UserService;
import lombok.AllArgsConstructor;
import org.docx4j.Docx4J;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.krysalis.barcode4j.impl.code128.Code128Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * The type Invoice pdf service. Used to connect controller with Data access object
 * @author Michal Goral
 * @version 1.0
 */
@Service
@AllArgsConstructor
public class InvoicePdfService {

    /**
     * the path to invoice document template
     */
    private final static String PATH = "src/main/resources/templates/invoice_template.docx";

    /**
     * the invoice service bean
     */
    private final InvoiceService invoiceService;

    /**
     * the customer service bean
     */
    private final CustomerService customerService;

    /**
     * the invoice item service bean
     */
    private final InvoiceItemService invoiceItemService;

    /**
     * the product service bean
     */
    private final ProductService productService;

    /**
     * the user service bean
     */
    private final UserService userService;

    /**
     * the company service bean
     */
    private final CompanyService companyService;


    /**
     * Generates docx file from template.
     *
     * @param invoiceId the invoice id
     * @return the byte array
     * @throws Exception the exception
     */
    public byte[] generateDocxFileFromTemplate(Long invoiceId) throws Exception {

        File invoiceTemplate = new File(PATH);
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(invoiceTemplate);
        MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
        VariablePrepare.prepare(wordMLPackage);

        InvoiceEntity invoiceEntity = invoiceService.getInvoice(invoiceId);
        CustomerEntity customerEntity = customerService.getCustomer((long) invoiceEntity.getCustomerId());
        InvoiceItemEntity invoiceItemEntity = invoiceItemService.getInvoiceItemFromInvoiceId(invoiceEntity.getId());
        ProductEntity productEntity = productService.getProduct((long) invoiceItemEntity.getProductId());

        InvoiceInformation invoiceInformation = new InvoiceInformation(
                invoiceEntity, customerEntity, invoiceItemEntity, productEntity,
                invoiceService.getInvoiceCurrencyCode(invoiceId),
                invoiceService.getPaymentMethod(invoiceId),
                userService.getCurrentUser(),
                companyService.getCurrentUserCompany()
        );
        HashMap<String, String> variables = invoiceInformation.getVariablesInHashMap();

        documentPart.variableReplace(variables);
        putBarcode(wordMLPackage, invoiceEntity.getInvoiceNumber());
        FileOutputStream os = new FileOutputStream("src/main/resources/static/temp.pdf");
        Docx4J.toPDF(wordMLPackage, os);
        Path pdfPath = Paths.get("src/main/resources/static/temp.pdf");
        byte[] pdf = Files.readAllBytes(pdfPath);
        os.flush();
        os.close();

        return pdf;

    }

    /**
     * Puts barcode to docx file.
     *
     * @param wordMLPackage the word ml package
     * @param barcodeText   the barcode text
     */
    public static void putBarcode(WordprocessingMLPackage wordMLPackage, String barcodeText) {

        try {
            byte[] bytes = getBarcode(barcodeText);
            BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(wordMLPackage, bytes);

            int docPrId = 1;
            int cNvPrId = 2;
            Inline inline = imagePart.createImageInline(
                    "Filename hint", "Alternative text", docPrId, cNvPrId, false);

            P paragraph = addInlineImageToParagraph(inline);
            wordMLPackage.getMainDocumentPart().addObject(paragraph);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates an object factory and use it to create a paragraph and a run.
     * Then adds the run to the paragraph. Next it creates a drawing and
     * adds it to the run. Finally it adds the inline object to the drawing and
     * return the paragraph.
     *
     * @param inline The inline object containing the image.
     * @return the paragraph containing the image
     */
    private static P addInlineImageToParagraph(Inline inline) {
        ObjectFactory factory = new ObjectFactory();
        P paragraph = factory.createP();
        R run = factory.createR();
        paragraph.getContent().add(run);
        Drawing drawing = factory.createDrawing();
        run.getContent().add(drawing);
        drawing.getAnchorOrInline().add(inline);
        return paragraph;
    }

    /**
     * Gets barcode image. It is generateb by  org.krysalis.barcode4j.output.bitmap
     * <a href="http://barcode4j.sourceforge.net/trunk/javadocs/org/krysalis/barcode4j/output/bitmap/BitmapCanvasProvider.html">See more</a>
     * @param barcodeText the text that barcode will be generated from
     * @return the barcode image in byte array
     */
    private static byte[] getBarcode(String barcodeText) {
        Code128Bean barcodeGenerator = new Code128Bean();
        BitmapCanvasProvider canvas =
                new BitmapCanvasProvider(160, BufferedImage.TYPE_BYTE_BINARY, false, 0);

        barcodeGenerator.generateBarcode(canvas, barcodeText);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bytes = new byte[0];
        try {
            ImageIO.write(canvas.getBufferedImage(), "png", byteArrayOutputStream);
            bytes = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes;
    }

}
