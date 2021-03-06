package pdvsimple.model;

import java.util.*;

import javax.persistence.*;

import org.apache.commons.beanutils.*;
import org.hibernate.validator.*;
import org.openxava.annotations.*;
import org.openxava.filters.*;
import org.openxava.jpa.*;
import org.openxava.util.*;
import org.openxava.validators.*;

import pdvsimple.actions.*;

/***
 * Antes de facturar una orden la orden debe estar entregada
 * @author Javier Paniza
 * 
 */

@Entity
@Views({
		@View(extendsView = "super.DEFAULT", members = "entregado; factura { factura }"),
		@View(name = "NoClienteNoFactura", members = "ano, codigo, fecha; detalles; notas") })
@Tabs({			//10.32 - 186 @Tabs es para definir varios tabs para la misma entidad
	@Tab(baseCondition = "eliminado = false",				//Tab sin nombre, es el predeterminado
			properties="ano, codigo, fecha, cliente.codigo, cliente.nombre, " +
						"entregado, beneficioEstimado, subTotal, " +
						"itbis, total, notas"),
	@Tab(name="Eliminado", baseCondition = "eliminado = true"),	//Tab con nombre
	/*@Tab(name="ClienteActual",		//13.6.4 - 270 Limitar visibilidad de los datos en el modo lista 
		baseCondition="cast (${cliente.codigo} as varchar ) = ?", filter=UserFilter.class, 
		properties="ano, codigo, fecha, cliente.codigo, cliente.nombre, entregado, importe, itbis, total")
	*/	 
	})
public class Orden extends DocumentoComercial {

	@ManyToOne
	@ReferenceView("NoClienteNoOrdenes")
	//@OnChange(MuestraOcultaCrearFacturaAction.class)	//11.13 - 202
	@OnChangeSearch(OnChangeSearchFacturaAction.class)	//12.7 - 232
	@SearchAction("Orden.buscarFactura")	//define nuestra accion para buscar facturas
	private Factura factura;
	
	/* 9.1 - 142 delivered como alternativas de validacion
	 * Un usuario no puede asignar pedidos a una factura 
	 * si los pedidos no han sido entregados
	 */
	@OnChange(MuestraOcultaCrearFacturaAction.class)	//11.13 - 202
	private boolean entregado;

	public Factura getFactura() {
		return factura;
	}

	public void setFactura(Factura factura) {
		this.factura = factura;
	}
	
	public boolean isEntregado() {
		return entregado;
	}
	
	public void setEntregado(boolean entregado) {
		this.entregado = entregado;
	}
	
	@AssertTrue		//antes de grabar confirma que el metodo devuelve true, si no lanza una excepcion
	private boolean isEntregadoParaSerFacturado() {
		return factura == null || isEntregado();	//la logica de validacion
	}
	
	@AssertTrue		//12.1 - 227 el cliente de la factura debe ser el mismo de la orden
	private boolean isClienteDeFacturaDebeSerIgual() {
		return factura == null ||		//factura es opcional
				factura.getCliente().getCodigo() == getCliente().getCodigo();
	}
	
	@PreRemove		//justo antes de borrar la entidad
	private void validateOnRemove() {
		if (factura != null) {		//la logica de validacion
			throw new IllegalStateException(	//lanza una excepcion runtime
				XavaResources.getString(		//para obtener un mensaje de texto i18n
						"no_se_puede_eliminar_orden_con_factura"));
		}
	}
	
	public void setEliminado(boolean eliminado) {
		if (eliminado) validateOnRemove();		//llamamos a la validacion explicitamente
		super.setEliminado(eliminado);
	}
	
	/***
	 * cuadro 11.5 - 197
	 * @throws ValidationException una excepcion de aplicacion (1)
	 */
	public void crearFactura() throws ValidationException	
	{
		if (this.factura != null) {			//si ya tiene factura no podemos crearla
			throw new ValidationException(	//admite un id de i18n como argumento
					"imposible_crear_factura_orden_ya_tiene_factura");
		}
		if (!isEntregado()) {		//si el pedido no eta entregado no podemo crear la factura
			throw new ValidationException(
					"imposible_crear_factura_si_orden_no_entregada");
		}
		try {
			Factura factura = new Factura();	//instancia una nueva factura
			BeanUtils.copyProperties(factura, this); 	//y copia el estado del pedido actual
			factura.setOid(null); 	//para que JPA sepa que esta entidad todavia no existe
			factura.setFecha(new Date());
			factura.setDetalles(new ArrayList<Detalle>());	//borra la coleccion de detalles
			XPersistence.getManager().persist(factura);
			copiaDetallesAFactura(factura);		//rellena la coleccion de detalles
			this.factura = factura;		//siempre despues de persist()
		} 
		catch (Exception ex) {		//cualquier excepcion inesperada (2)
			throw new SystemException(		//se lanza una excepcion runtime (3)
					"imposible_crear_factura", ex);
		}
	}
	
	/***
	 * cuadro 11.8 - 198 copiar una coleccion de entidad a entidad
	 * @param factura
	 * @throws Exception
	 */
	public void copiaDetallesAFactura(Factura factura) {
		try {
			for (Detalle detalleOrden : getDetalles()) {	//itera por los detalles del pedido actual
				Detalle detalleFactura =		//clona el detalle (1) 
						(Detalle) BeanUtils.cloneBean(detalleOrden);
				detalleFactura.setOid(null); 	//para ser grabada como una nueva entidad (2)
				detalleFactura.setPadre(factura); 	//el punto clave: poner un nuevo padre (3)
				XPersistence.getManager().persist(detalleFactura); 	//(4)
			}
		}
		catch (Exception ex) {		//asi convertimos cualquier excepcion
			throw new SystemException(		//en una excepcion runtime
					"imposible_copiar_detalles_a_factura", ex);
		}
	}
	
	/***
	 * 12.17 - 240
	 */
	public void copiaDetallesAFactura() {
		copiaDetallesAFactura(getFactura());	//delegamos en un metodo ya existente
	}

}
